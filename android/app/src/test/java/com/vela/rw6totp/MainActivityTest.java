package com.vela.rw6totp;

import static org.junit.Assert.*;
import android.app.AlertDialog;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowAlertDialog;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class MainActivityTest {
    private List<View> walk(View root){
        List<View> result=new ArrayList<>();result.add(root);
        if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++)result.addAll(walk(((ViewGroup)root).getChildAt(i)));
        return result;
    }
    @Test public void launchesWithOnlyTheRequestedTitleListAndThreeButtons(){
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
            List<View> views=walk(controller.get().getWindow().getDecorView());
            List<String> buttons=new ArrayList<>(),text=new ArrayList<>();
            for(View view:views){if(view instanceof Button)buttons.add(((Button)view).getText().toString());if(view instanceof TextView)text.add(((TextView)view).getText().toString());}
            assertEquals(List.of("계정 추가","수정","동기화"),buttons);
            assertTrue(text.contains("RW6 TOTP 동기화"));assertTrue(text.contains("TOTP 목록"));
            assertTrue(text.stream().noneMatch(s->s.contains("QR") || s.contains("Mi Fitness") || s.contains("URI")));
        }
    }
    @Test public void addAccountOpensProtectedManualForm(){
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
            for(View view:walk(controller.get().getWindow().getDecorView()))
                if(view instanceof Button && "계정 추가".contentEquals(((Button)view).getText()))view.performClick();
            AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();assertNotNull(dialog);assertTrue(dialog.isShowing());
            assertTrue((dialog.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_SECURE)!=0);
            long fields=walk(dialog.getWindow().getDecorView()).stream().filter(v->v instanceof EditText).count();assertEquals(5,fields);
            dialog.dismiss();
        }
    }
}
