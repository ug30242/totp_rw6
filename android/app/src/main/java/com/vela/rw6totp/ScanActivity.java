package com.vela.rw6totp;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.BarcodeView;
import com.journeyapps.barcodescanner.DefaultDecoderFactory;
import java.util.Collections;

public final class ScanActivity extends Activity {
    private BarcodeView scanner;
    private boolean finished;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom()); return insets;
        });
        TextView help = new TextView(this); help.setText("계정의 TOTP QR 코드를 비춰 주세요.\n스캔 결과는 인터넷으로 열지 않습니다."); help.setTextSize(18); help.setPadding(16,16,16,16); root.addView(help);
        scanner = new BarcodeView(this);
        scanner.setDecoderFactory(new DefaultDecoderFactory(Collections.singletonList(BarcodeFormat.QR_CODE)));
        root.addView(scanner, new LinearLayout.LayoutParams(-1,0,1));
        Button close = new Button(this); close.setText("취소"); close.setOnClickListener(v -> finish()); root.addView(close);
        setContentView(root);
        scanner.decodeSingle(result -> {
            if (finished || result == null) return;
            finished = true; setResult(RESULT_OK, new Intent().putExtra("qr", result.getText())); finish();
        });
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 1);
    }
    @Override public void onResume() {
        super.onResume();
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) scanner.resume();
    }
    @Override public void onPause() { scanner.pause(); super.onPause(); }
    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == 1 && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) scanner.resume();
        else finish();
    }
}
