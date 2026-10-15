package com.vela.rw6totp;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.xiaomi.xms.wearable.node.Node;
import java.util.ArrayList;
import java.util.List;

public final class MainActivity extends Activity implements XmsClient.Events {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<OtpAccount> accounts = new ArrayList<>();
    private LinearLayout accountList;
    private TextView count;
    private Button addButton,editButton,syncButton;
    private XmsClient client;
    private SyncSession sync;
    private AccountVault vault;
    private AlertDialog editDialog;
    private boolean authorizing,vaultLoaded,connecting,autoSync;
    private int selected=-1;
    private String latestStatus="";
    private final Runnable connectTimeout=() -> {
        connecting=false;authorizing=false;autoSync=false;client.disconnect();
        notifyUser("워치 연결 시간 초과");updateButtons();
    };
    private int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }
    private GradientDrawable background(int color,int stroke) {
        GradientDrawable drawable=new GradientDrawable();drawable.setColor(color);drawable.setCornerRadius(dp(16));
        if(stroke!=0)drawable.setStroke(dp(1),stroke);return drawable;
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        int canvas=Color.rgb(245,247,251),ink=Color.rgb(20,32,51);
        getWindow().setStatusBarColor(canvas);getWindow().setNavigationBarColor(canvas);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(canvas);
        root.setOnApplyWindowInsetsListener((view,insets) -> {
            view.setPadding(dp(24)+insets.getSystemWindowInsetLeft(),dp(24)+insets.getSystemWindowInsetTop(),
                dp(24)+insets.getSystemWindowInsetRight(),dp(20)+Math.max(insets.getSystemWindowInsetBottom(),insets.getStableInsetBottom()));return insets;
        });
        setContentView(root);
        TextView title=text("RW6 TOTP 동기화",28,ink,true);title.setPadding(0,0,0,dp(28));root.addView(title);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(text("TOTP 목록",19,ink,true),new LinearLayout.LayoutParams(0,-2,1));
        count=text("0",14,Color.rgb(100,116,139),false);header.addView(count);root.addView(header);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);scroll.setPadding(0,dp(16),0,dp(12));
        accountList=new LinearLayout(this);accountList.setOrientation(LinearLayout.VERTICAL);scroll.addView(accountList);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout editRow=new LinearLayout(this);
        addButton=button("계정 추가",false,v -> { if(canEdit())editAccount(-1); });
        editButton=button("수정",false,v -> chooseAccount());
        LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,dp(52),1);left.setMargins(0,0,dp(6),0);
        LinearLayout.LayoutParams right=new LinearLayout.LayoutParams(0,dp(52),1);right.setMargins(dp(6),0,0,0);
        editRow.addView(addButton,left);editRow.addView(editButton,right);root.addView(editRow);
        syncButton=button("동기화",true,v -> {
            if(!canEdit() || accounts.isEmpty())return;
            if(client.isReady()){sync.start(accounts);updateButtons();}
            else{autoSync=true;beginConnect();}
        });
        LinearLayout.LayoutParams syncLayout=new LinearLayout.LayoutParams(-1,dp(56));syncLayout.setMargins(0,dp(12),0,0);root.addView(syncButton,syncLayout);
        client=new XmsClient(this,this);vault=new AccountVault(this);
        sync=new SyncSession(new SyncSession.Transport(){
            @Override public void send(String packet,boolean probe){client.send(packet,probe);}
            @Override public void openWatch(){client.openWatch();}
        },new SyncSession.Events(){
            @Override public void status(String message){latestStatus=message;if(syncButton!=null)syncButton.setText(message);}
            @Override public void finished(boolean success){notifyUser(latestStatus);updateButtons();}
        },(action,millis) -> main.postDelayed(action,millis));
        updateButtons();
    }
    private TextView text(String value,int size,int color,boolean bold) {
        TextView view=new TextView(this);view.setText(value);view.setTextSize(size);view.setTextColor(color);
        view.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));return view;
    }
    private Button button(String value,boolean primary,View.OnClickListener click) {
        Button button=new Button(this);button.setText(value);button.setTextSize(16);button.setAllCaps(false);
        button.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        button.setTextColor(primary?Color.WHITE:Color.rgb(37,99,235));
        button.setBackground(background(primary?Color.rgb(37,99,235):Color.WHITE,primary?0:Color.rgb(219,226,237)));
        button.setOnClickListener(click);return button;
    }
    private void notifyUser(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
    private boolean canEdit(){return vaultLoaded && !connecting && !sync.isRunning();}
    private void updateButtons(){
        boolean busy=connecting || (sync!=null && sync.isRunning());
        if(addButton!=null)addButton.setEnabled(vaultLoaded && !busy);
        if(editButton!=null)editButton.setEnabled(vaultLoaded && !accounts.isEmpty() && !busy);
        if(syncButton!=null){syncButton.setEnabled(vaultLoaded && !accounts.isEmpty() && !busy);if(!busy)syncButton.setText("동기화");}
    }
    private void beginConnect(){
        connecting=true;syncButton.setText("연결 중…");updateButtons();
        main.removeCallbacks(connectTimeout);main.postDelayed(connectTimeout,12000);client.connect();
    }
    private void renderList(){
        accountList.removeAllViews();count.setText(String.valueOf(accounts.size()));
        if(accounts.isEmpty()){
            TextView empty=text("등록된 계정 없음",16,Color.rgb(148,163,184),false);empty.setGravity(Gravity.CENTER);empty.setPadding(0,dp(56),0,0);accountList.addView(empty);
        }
        for(int i=0;i<accounts.size();i++){
            final int index=i;OtpAccount account=accounts.get(i);
            LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(18),dp(16),dp(18),dp(16));
            card.setBackground(background(Color.WHITE,index==selected?Color.rgb(37,99,235):Color.rgb(230,235,243)));
            card.addView(text(account.name,18,Color.rgb(20,32,51),true));
            TextView detail=text(account.algorithm+" · "+account.digits+"자리 · "+account.period+"초",13,Color.rgb(100,116,139),false);
            detail.setPadding(0,dp(6),0,0);card.addView(detail);card.setOnClickListener(v -> {if(canEdit()){selected=index;renderList();}});
            LinearLayout.LayoutParams row=new LinearLayout.LayoutParams(-1,-2);row.setMargins(0,0,0,dp(10));accountList.addView(card,row);
        }
        updateButtons();
    }
    private void loadVault(){
        accounts.clear();vaultLoaded=false;
        try{accounts.addAll(vault.read());vaultLoaded=true;}
        catch(Exception e){notifyUser("계정 목록 읽기 실패. 기존 파일은 유지됩니다.");}
        if(selected>=accounts.size())selected=-1;renderList();
    }
    private boolean persist(OtpAccount account,int index) {
        if (!canEdit()) return false;
        List<OtpAccount> changed = new ArrayList<>(accounts);
        for (int i=0;i<changed.size();i++) if (i != index && changed.get(i).identity().equals(account.identity())) {
            notifyUser("이미 같은 계정이 있습니다."); return false;
        }
        if (index >= 0) changed.set(index,account); else changed.add(account);
        if (changed.size() > 50) { notifyUser("최대 50개 계정을 지원합니다."); return false; }
        try {
            vault.write(changed); accounts.clear(); accounts.addAll(changed); renderList();
            notifyUser("저장했습니다."); return true;
        } catch (Exception e) { notifyUser("암호화 저장에 실패했습니다. 기존 목록은 유지됩니다."); return false; }
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
            .setView(scroll).setPositiveButton("저장",null).setNegativeButton("취소",null).create();
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
        if (selected >= 0 && selected < accounts.size()) { showAccountActions(selected); return; }
        String[] names = new String[accounts.size()]; for (int i=0;i<names.length;i++) names[i]=accounts.get(i).name;
        new AlertDialog.Builder(this).setTitle("계정 선택").setItems(names,(dialog,index) -> showAccountActions(index)).show();
    }
    private void showAccountActions(int index) {
        if (!canEdit() || index < 0 || index >= accounts.size()) return;
        new AlertDialog.Builder(this).setTitle(accounts.get(index).name)
            .setItems(new String[]{"수정", "삭제"},(dialog,action) -> {
                if (action == 0) editAccount(index);
                else new AlertDialog.Builder(this).setMessage("계정을 삭제할까요?")
                    .setPositiveButton("삭제",(a,b) -> {
                        if (!canEdit() || index >= accounts.size()) return;
                        List<OtpAccount> changed = new ArrayList<>(accounts); changed.remove(index);
                        try { vault.write(changed); accounts.clear(); accounts.addAll(changed); selected=-1; renderList(); }
                        catch (Exception e) { notifyUser("삭제 실패. 기존 목록은 유지됩니다."); }
                    }).setNegativeButton("취소",null).show();
            }).show();
    }
    @Override public void nodes(List<Node> nodes){
        main.removeCallbacks(connectTimeout);
        if(nodes.size()==1){main.postDelayed(connectTimeout,12000);client.select(nodes.get(0));return;}
        String[] names=new String[nodes.size()];for(int i=0;i<names.length;i++)names[i]=nodes.get(i).name==null?"Watch "+(i+1):nodes.get(i).name;
        new AlertDialog.Builder(this).setTitle("워치 선택").setItems(names,(dialog,index) -> {
            main.postDelayed(connectTimeout,12000);client.select(nodes.get(index));
        }).setOnCancelListener(dialog -> {connecting=false;autoSync=false;client.disconnect();updateButtons();}).show();
    }
    @Override public void ready(String name){
        main.removeCallbacks(connectTimeout);connecting=false;authorizing=false;
        if(autoSync){autoSync=false;sync.start(accounts);}updateButtons();
    }
    @Override public void error(String message){
        autoSync=false;connecting=false;authorizing=false;main.removeCallbacks(connectTimeout);
        if(sync.isRunning())sync.fail(message);else notifyUser(message);updateButtons();
    }
    @Override public void message(String json){sync.receive(json);}
    @Override public void authorizing(){
        authorizing=true;main.removeCallbacks(connectTimeout);main.postDelayed(connectTimeout,120000);syncButton.setText("권한 확인 중…");
    }
    @Override protected void onStart(){super.onStart();loadVault();}
    @Override protected void onStop(){
        if(!authorizing){
            if(editDialog!=null)editDialog.dismiss();sync.cancel();connecting=false;autoSync=false;
            main.removeCallbacks(connectTimeout);client.disconnect();accounts.clear();vaultLoaded=false;renderList();
        }
        super.onStop();
    }
    @Override protected void onDestroy(){main.removeCallbacksAndMessages(null);client.destroy();super.onDestroy();}
}
