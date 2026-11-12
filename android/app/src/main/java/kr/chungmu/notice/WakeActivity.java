package kr.chungmu.notice;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;

/**
 * 화면이 꺼진(대기) 상태일 때 화면을 켜기 위한 투명 화면.
 * 안드로이드는 덧씌우는 창으로는 화면을 켜 주지 않고, ‘화면(Activity)’이 떠야 켜 준다.
 * 화면을 켠 뒤 몇 초 있다가 스스로 닫히고, 공지나 동영상 창은 그대로 남는다.
 */
public class WakeActivity extends Activity {

    static void wake(Context c) {
        try {
            Intent i = new Intent(c, WakeActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_NO_ANIMATION
                    | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            c.startActivity(i);
        } catch (Exception e) {
            Prefs.setLastEvent(c, "화면 켜기 화면을 띄우지 못함: " + e.getMessage());
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        if (Build.VERSION.SDK_INT >= 26) {
            KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
            if (km != null) {
                try { km.requestDismissKeyguard(this, null); } catch (Exception ignored) {}
            }
        }
        new Handler(Looper.getMainLooper()).postDelayed(this::finish, 4000);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, 0);
    }
}
