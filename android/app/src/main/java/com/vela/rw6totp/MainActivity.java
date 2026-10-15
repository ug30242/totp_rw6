package com.vela.rw6totp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.xiaomi.xms.wearable.node.Node;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.ArrayList;

public final class MainActivity extends Activity implements XmsClient.Events {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService images = Executors.newSingleThreadExecutor();
    private final List<OtpAccount> accounts = new ArrayList<>();
    private TextView status, preview, accountList;
    private EditText input;
    private Button addDraft, syncButton;
    private XmsClient client;
    private SyncSession sync;
    private AccountVault vault;
    private OtpAccount draft;
    private AlertDialog editDialog;
    private boolean external, authorizing, vaultLoaded, connecting, autoSync;
    private int generation;
    private final Runnable connectTimeout = () -> {
        connecting = false; authorizing = false; autoSync = false; client.disconnect();
        status.setText("연결 시간이 초과되었습니다. Mi Fitness의 워치 연결과 권한을 확인하세요."); updateButtons();
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int)(20 * getResources().getDisplayMetrics().density);
        scroll.setOnApplyWindowInsetsListener((view,insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom()); return insets;
        });
        root.setPadding(pad,pad,pad,pad); scroll.addView(root); setContentView(scroll);
        text(root,"RW6 TOTP",28);
        text(root,"Android 계정 목록 → 동기화 → 워치\nQR 없이 이름과 인증키를 직접 추가할 수 있습니다.\nMi Fitness(중국판 운동건강)에 워치를 연결하세요. Notify만으로는 전송할 수 없습니다.\n휴대폰 계정은 Android Keystore로 암호화해 보관합니다.",16);
        status = text(root,"계정을 추가한 뒤 동기화를 누르세요.",17);
        client = new XmsClient(this,this); vault = new AccountVault(this);
        sync = new SyncSession(new SyncSession.Transport() {
            @Override public void send(String packet) { client.send(packet,() -> {}); }
            @Override public void openWatch() { client.openWatch(); }
        },new SyncSession.Events() {
            @Override public void status(String message) { status.setText(message); }
            @Override public void finished(boolean success) { updateButtons(); }
        },(action,millis) -> main.postDelayed(action,millis));
        accountList = text(root,"",18);
        button(root,"계정 직접 추가",v -> { if (canEdit()) editAccount(-1); });
        button(root,"계정 수정 / 휴대폰에서 삭제",v -> chooseAccount());
        syncButton = button(root,"워치로 동기화",v -> {
            if (!canEdit() || accounts.isEmpty() || connecting) return;
            clearDraft();
            if (client.isReady()) { sync.start(accounts); updateButtons(); }
            else { autoSync = true; beginConnect(); }
        });
        text(root,"동기화는 휴대폰 계정을 워치에 추가·갱신합니다. 다시 동기화해도 중복 추가하지 않습니다. 휴대폰에서 삭제해도 워치 계정은 유지되며, 워치에서 직접 삭제할 수 있습니다.",15);
        button(root,"워치 다시 연결",v -> { if (!sync.isRunning() && !connecting) { autoSync = false; beginConnect(); } });
        text(root,"선택 사항: 기존 계정 QR / URI 가져오기",19);
        button(root,"계정 QR 카메라 스캔",v -> {
            if (!canEdit()) return;
            clearDraft(); external = true; startActivityForResult(new Intent(this,ScanActivity.class),1);
        });
        button(root,"QR 이미지 선택",v -> {
            if (!canEdit()) return;
            clearDraft(); external = true;
            startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE),2);
        });
        input = new EditText(this); input.setHint("또는 otpauth://totp/… URI 붙여넣기");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setSaveEnabled(false); input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(4096)}); root.addView(input);
        button(root,"붙여넣은 URI 확인",v -> { if (canEdit()) { String uri = input.getText().toString(); input.setText(""); parse(uri); } });
        preview = text(root,"아직 읽은 계정이 없습니다.",18);
        addDraft = button(root,"읽은 계정을 휴대폰 목록에 추가",v -> { if (draft != null) persist(draft,-1); });
        button(root,"임시 입력 지우기",v -> { clearDraft(); status.setText("임시 입력을 지웠습니다. 저장한 계정은 유지됩니다."); });
        text(root,"동기화는 외부 서버를 사용하지 않습니다. 워치 TOTP 화면이 열려 있어야 수신할 수 있습니다. 자동 실행되지 않으면 워치에서 + Phone을 여세요.\n휴대폰은 워치의 저장 완료 응답을 받은 뒤에만 성공을 표시합니다. 원본 인증기와 복구 코드를 유지하세요.",15);
        updateButtons();
    }
    private TextView text(LinearLayout root,String value,int size) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setPadding(0,12,0,12); root.addView(view); return view;
    }
    private Button button(LinearLayout root,String value,View.OnClickListener click) {
        Button b = new Button(this); b.setText(value); b.setOnClickListener(click); root.addView(b); return b;
    }
    private boolean canEdit() { return vaultLoaded && !sync.isRunning() && !connecting; }
    private void updateButtons() {
        boolean busy = sync != null && sync.isRunning();
        if (addDraft != null) addDraft.setEnabled(vaultLoaded && draft != null && !busy);
        if (syncButton != null) syncButton.setEnabled(vaultLoaded && !accounts.isEmpty() && !busy && !connecting);
    }
    private void beginConnect() {
        connecting = true; status.setText("Mi Fitness 연결 확인 중…"); updateButtons();
        main.removeCallbacks(connectTimeout); main.postDelayed(connectTimeout,12000); client.connect();
    }
    private void renderList() {
        StringBuilder list = new StringBuilder("휴대폰 계정: " + accounts.size() + " / 50\n");
        for (OtpAccount a : accounts) list.append(a.name).append("  ").append(a.algorithm).append(" / ").append(a.digits).append(" / ").append(a.period).append("s\n");
        accountList.setText(list); updateButtons();
    }
    private void loadVault() {
        accounts.clear(); vaultLoaded = false;
        try { accounts.addAll(vault.read()); vaultLoaded = true; }
        catch (Exception e) { status.setText("암호화된 계정 목록을 읽지 못했습니다. 파일은 보존했고 덮어쓰지 않습니다."); }
        renderList();
    }
    private boolean persist(OtpAccount account,int index) {
        if (!canEdit()) return false;
        List<OtpAccount> changed = new ArrayList<>(accounts);
        for (int i=0;i<changed.size();i++) if (i != index && changed.get(i).identity().equals(account.identity())) {
            status.setText("이미 같은 계정이 있습니다."); return false;
        }
        if (index >= 0) changed.set(index,account); else changed.add(account);
        if (changed.size() > 50) { status.setText("최대 50개 계정을 지원합니다."); return false; }
        try {
            vault.write(changed); accounts.clear(); accounts.addAll(changed); clearDraft(); renderList();
            status.setText("휴대폰에 암호화해 저장했습니다. 동기화를 누르면 워치에 전송합니다."); return true;
        } catch (Exception e) { status.setText("암호화 저장에 실패했습니다. 기존 목록은 유지됩니다."); return false; }
    }
    private EditText field(LinearLayout form,String hint,String value,boolean secret) {
        EditText edit = new EditText(this); edit.setHint(hint); edit.setText(value);
        edit.setSaveEnabled(false); edit.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        edit.setInputType(InputType.TYPE_CLASS_TEXT | (secret ? InputType.TYPE_TEXT_VARIATION_PASSWORD : InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS));
        edit.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(secret ? 256 : 64)});
        form.addView(edit); return edit;
    }
    private void editAccount(int index) {
        LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(24,8,24,8);
        OtpAccount old = index < 0 ? null : accounts.get(index);
        EditText name = field(form,"계정 이름",old == null ? "" : old.name,false);
        EditText secret = field(form,"Base32 인증키",old == null ? "" : old.secret,true);
        EditText algorithm = field(form,"SHA1 / SHA256 / SHA512",old == null ? "SHA1" : old.algorithm,false);
        EditText digits = field(form,"6 / 8 자리",old == null ? "6" : ""+old.digits,false);
        EditText period = field(form,"주기 (초)",old == null ? "30" : ""+old.period,false);
        ScrollView scroll = new ScrollView(this); scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(index < 0 ? "계정 추가" : "계정 수정")
            .setView(scroll).setPositiveButton("휴대폰에 저장",null).setNegativeButton("취소",null).create();
        if (dialog.getWindow() != null) dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        editDialog = dialog;
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                OtpAccount a = OtpAccount.direct(name.getText().toString(),secret.getText().toString(),algorithm.getText().toString().trim().toUpperCase(java.util.Locale.ROOT),digits.getText().toString().trim(),period.getText().toString().trim());
                if (old != null) a = a.withSyncId(old.syncId);
                if (persist(a,index)) dialog.dismiss();
            } catch (IllegalArgumentException e) { secret.setError("인증키와 TOTP 설정을 확인하세요."); }
        }));
        dialog.setOnDismissListener(ignored -> { secret.setText(""); name.setText(""); editDialog = null; }); dialog.show();
    }
    private void chooseAccount() {
        if (!canEdit() || accounts.isEmpty()) return;
        String[] names = new String[accounts.size()]; for (int i=0;i<names.length;i++) names[i]=accounts.get(i).name;
        new AlertDialog.Builder(this).setTitle("계정 선택").setItems(names,(dialog,index) ->
            new AlertDialog.Builder(this).setTitle(names[index]).setItems(new String[]{"수정","휴대폰에서 삭제"},(d,action) -> {
                if (action == 0) editAccount(index);
                else new AlertDialog.Builder(this).setMessage("휴대폰에서 삭제할까요? 워치의 계정은 유지됩니다.").setPositiveButton("삭제",(a,b) -> {
                    if (!canEdit() || index >= accounts.size()) return;
                    List<OtpAccount> changed = new ArrayList<>(accounts); changed.remove(index);
                    try { vault.write(changed); accounts.clear(); accounts.addAll(changed); renderList(); }
                    catch (Exception e) { status.setText("삭제 내용을 저장하지 못했습니다. 기존 목록은 유지됩니다."); }
                }).setNegativeButton("취소",null).show();
            }).show()).show();
    }
    private void clearDraft() {
        generation++; draft = null;
        if (input != null) input.setText(""); if (preview != null) preview.setText("임시 계정 없음"); updateButtons();
    }
    private void parse(String uri) {
        clearDraft();
        try {
            draft = OtpAccount.parse(uri);
            preview.setText(draft.name + "\n" + draft.algorithm + " / " + draft.digits + " digits / " + draft.period + "s\nSecret: " + draft.secret.length() + " characters (hidden)");
            status.setText("계정 정보를 읽었습니다. 휴대폰 목록에 추가한 뒤 동기화하세요.");
        } catch (IllegalArgumentException e) { status.setText(e.getMessage()); }
        updateButtons();
    }
    @Override public void nodes(List<Node> nodes) {
        main.removeCallbacks(connectTimeout);
        if (nodes.size() == 1) { main.postDelayed(connectTimeout,12000); client.select(nodes.get(0)); return; }
        String[] names = new String[nodes.size()];
        for (int i=0;i<names.length;i++) names[i]=nodes.get(i).name == null ? "Watch " + (i+1) : nodes.get(i).name;
        new AlertDialog.Builder(this).setTitle("전송할 워치 선택").setItems(names,(dialog,index) -> {
            status.setText("워치 앱과 서명을 확인하는 중…"); main.postDelayed(connectTimeout,12000); client.select(nodes.get(index));
        }).setOnCancelListener(dialog -> { connecting = false; autoSync = false; client.disconnect(); status.setText("연결을 취소했습니다."); updateButtons(); }).show();
    }
    @Override public void ready(String name) {
        main.removeCallbacks(connectTimeout); connecting = false; authorizing = false;
        status.setText((name == null ? "워치" : name) + " 연결됨.");
        if (autoSync) { autoSync = false; sync.start(accounts); } updateButtons();
    }
    @Override public void error(String message) {
        autoSync = false; connecting = false; authorizing = false; main.removeCallbacks(connectTimeout);
        sync.fail(message); status.setText(message); updateButtons();
    }
    @Override public void message(String json) { sync.receive(json); }
    @Override public void authorizing() {
        authorizing = true; main.removeCallbacks(connectTimeout); main.postDelayed(connectTimeout,120000);
        status.setText("Mi Fitness에서 RW6 TOTP의 기기 통신 권한을 허용하세요.");
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data); external = false;
        if (result != RESULT_OK || data == null) return;
        if (request == 1) parse(data.getStringExtra("qr"));
        if (request == 2 && data.getData() != null) decodeImage(data.getData());
    }
    private void decodeImage(Uri uri) {
        final int epoch = ++generation;
        status.setText("이 기기에서 QR 이미지를 읽는 중…");
        images.execute(() -> {
            Bitmap bitmap = null; byte[] bytes = null; int[] pixels = null;
            try (InputStream stream = getContentResolver().openInputStream(uri); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                if (stream == null) throw new Exception();
                byte[] buffer = new byte[8192]; int count;
                while ((count = stream.read(buffer)) != -1) {
                    if (out.size() + count > 10 * 1024 * 1024) throw new Exception();
                    out.write(buffer,0,count);
                }
                bytes = out.toByteArray();
                BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || (long)bounds.outWidth * bounds.outHeight > 16000000L) throw new Exception();
                BitmapFactory.Options options = new BitmapFactory.Options(); options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                bitmap = BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
                if (bitmap == null) throw new Exception();
                int w = bitmap.getWidth(), h = bitmap.getHeight(); pixels = new int[w*h]; bitmap.getPixels(pixels,0,w,0,0,w,h);
                Map<DecodeHintType,Object> hints = new EnumMap<>(DecodeHintType.class);
                hints.put(DecodeHintType.POSSIBLE_FORMATS, java.util.Collections.singletonList(com.google.zxing.BarcodeFormat.QR_CODE));
                hints.put(DecodeHintType.TRY_HARDER, true); hints.put(DecodeHintType.ALSO_INVERTED, true);
                String value = new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(w,h,pixels))), hints).getText();
                main.post(() -> { if (epoch == generation && !isFinishing() && !isDestroyed()) parse(value); });
            } catch (Exception e) {
                main.post(() -> { if (epoch == generation && !isFinishing() && !isDestroyed()) status.setText("QR을 읽지 못했습니다. 10MB / 1600만 화소 이하의 선명한 계정 QR 이미지를 선택하세요."); });
            } finally {
                if (bitmap != null) bitmap.recycle(); if (bytes != null) Arrays.fill(bytes,(byte)0); if (pixels != null) Arrays.fill(pixels,0);
            }
        });
    }
    @Override protected void onStart() { super.onStart(); loadVault(); }
    @Override protected void onStop() {
        if (!external && !authorizing) {
            if (editDialog != null) editDialog.dismiss();
            clearDraft(); sync.cancel(); connecting = false; autoSync = false;
            main.removeCallbacks(connectTimeout); client.disconnect(); accounts.clear(); vaultLoaded = false; renderList();
            status.setText("휴대폰 계정은 암호화 보관 중입니다. 동기화를 누르면 다시 연결합니다.");
        }
        super.onStop();
    }
    @Override protected void onDestroy() {
        generation++; main.removeCallbacksAndMessages(null); client.destroy(); images.shutdownNow(); super.onDestroy();
    }
}
