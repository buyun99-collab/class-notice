package kr.chungmu.notice;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Path;
import android.os.Build;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;

/**
 * 제조사 자체 절전(백라이트만 끄고 ‘두 번 터치’로 깨우는 방식) 칠판을 깨우기 위한 접근성 서비스.
 * 공지 창이 뜬 뒤 화면 왼쪽 위 빈 곳을 두 번 두드린다. 화면 내용은 읽지 않는다.
 */
public class WakeTapService extends AccessibilityService {
    private static WakeTapService instance;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        instance = null;
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}

    /** 접근성 설정에서 이 서비스가 켜져 있는지 */
    static boolean isEnabled(Context c) {
        try {
            String list = Settings.Secure.getString(c.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (list == null) return false;
            String me = new ComponentName(c, WakeTapService.class).flattenToString();
            String meShort = new ComponentName(c, WakeTapService.class).flattenToShortString();
            for (String s : list.split(":")) {
                if (s.equalsIgnoreCase(me) || s.equalsIgnoreCase(meShort)) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    /** 화면 왼쪽 위(공지 창의 빈 곳)를 두 번 두드림. 성공적으로 요청했으면 true */
    static boolean doubleTap() {
        WakeTapService s = instance;
        if (s == null || Build.VERSION.SDK_INT < 24) return false;
        // 우리 공지나 동영상 창이 화면을 덮고 있을 때만 두드린다.
        // 창이 없으면 두드림이 바탕화면이나 다른 앱에 닿아 엉뚱한 프로그램이 열릴 수 있음
        if (!OverlayPopup.isShowing() && !VideoOverlay.isPlaying() && !PartsPlayer.isShowing()) {
            Prefs.setLastEvent(s, "공지 창이 없어 두 번 두드리기를 건너뜀 (‘다른 앱 위에 표시’ 권한 확인)");
            return false;
        }
        try {
            int w = s.getResources().getDisplayMetrics().widthPixels;
            int h = s.getResources().getDisplayMetrics().heightPixels;
            float x = Math.max(10, w * 0.02f), y = Math.max(10, h * 0.03f);
            Path p1 = new Path(); p1.moveTo(x, y);
            Path p2 = new Path(); p2.moveTo(x, y);
            GestureDescription g = new GestureDescription.Builder()
                    .addStroke(new GestureDescription.StrokeDescription(p1, 0, 60))
                    .addStroke(new GestureDescription.StrokeDescription(p2, 180, 60))
                    .build();
            return s.dispatchGesture(g, null, null);
        } catch (Exception e) {
            return false;
        }
    }
}
