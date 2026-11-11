package kr.chungmu.notice;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 다른 앱(PPT, 유튜브 등) 위에 덮어 뜨는 공지 팝업 */
final class OverlayPopup {
    private static final Handler H = new Handler(Looper.getMainLooper());
    private static WindowManager wm;
    private static View current;
    private static TextView countdown;
    private static int remaining;

    private static final Runnable TICK = new Runnable() {
        @Override public void run() {
            remaining--;
            if (remaining <= 0) { dismiss(); return; }
            if (countdown != null) countdown.setText(remaining + "초 후 자동으로 닫힙니다");
            H.postDelayed(this, 1000);
        }
    };

    private OverlayPopup() {}

    static boolean canShow(Context c) {
        return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(c);
    }

    @SuppressWarnings("deprecation")
    static void show(Context c, Notice n) {
        if (!canShow(c)) {
            Prefs.setStatus(c, "공지를 받았지만 ‘다른 앱 위에 표시’ 권한이 없어 띄우지 못함");
            return;
        }
        dismiss();
        wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) return;

        int w = c.getResources().getDisplayMetrics().widthPixels;
        int h = c.getResources().getDisplayMetrics().heightPixels;
        int base = Math.max(w, h);   // 큰 칠판일수록 글자도 커지도록 화면 크기에 비례
        int accent = n.urgent ? Color.rgb(211, 47, 47) : Color.rgb(21, 101, 192);

        FrameLayout root = new FrameLayout(c);
        root.setBackgroundColor(0xCC000000);

        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(base / 80f);
        bg.setStroke(base / 300, accent);
        card.setBackground(bg);
        int pad = base / 50;
        card.setPadding(pad, pad, pad, pad);

        // 머리띠
        TextView head = new TextView(c);
        String time = new SimpleDateFormat("a h:mm", Locale.KOREA).format(new Date());
        head.setText((n.urgent ? "🚨 긴급 공지" : "📢 공지") + "   " + time);
        head.setTextColor(Color.WHITE);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        head.setTextSize(TypedValue.COMPLEX_UNIT_PX, base / 60f);
        GradientDrawable hb = new GradientDrawable();
        hb.setColor(accent);
        hb.setCornerRadius(base / 120f);
        head.setBackground(hb);
        head.setPadding(pad / 2, pad / 4, pad / 2, pad / 4);
        card.addView(head, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // 제목
        TextView title = new TextView(c);
        title.setText(n.title);
        title.setTextColor(Color.rgb(20, 20, 20));
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX, base / 30f);
        title.setPadding(0, pad / 2, 0, pad / 3);
        card.addView(title);

        // 내용 (길면 스크롤)
        if (!n.body.trim().isEmpty()) {
            ScrollView sv = new ScrollView(c);
            TextView body = new TextView(c);
            body.setText(n.body);
            body.setTextColor(Color.rgb(50, 50, 50));
            body.setTextSize(TypedValue.COMPLEX_UNIT_PX, base / 42f);
            body.setLineSpacing(0, 1.25f);
            sv.addView(body);
            card.addView(sv, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }

        // 아래쪽: 남은 시간 + 닫기 버튼
        LinearLayout bottom = new LinearLayout(c);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setPadding(0, pad / 2, 0, 0);

        countdown = new TextView(c);
        countdown.setTextColor(Color.GRAY);
        countdown.setTextSize(TypedValue.COMPLEX_UNIT_PX, base / 80f);
        bottom.addView(countdown, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button ok = new Button(c);
        ok.setText("확인");
        ok.setAllCaps(false);
        ok.setTextColor(Color.WHITE);
        ok.setTextSize(TypedValue.COMPLEX_UNIT_PX, base / 55f);
        GradientDrawable ob = new GradientDrawable();
        ob.setColor(accent);
        ob.setCornerRadius(base / 120f);
        ok.setBackground(ob);
        ok.setPadding(pad, pad / 3, pad, pad / 3);
        ok.setOnClickListener(v -> dismiss());
        bottom.addView(ok);
        card.addView(bottom);

        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(
                (int) (w * 0.78f), FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        root.addView(card, cardLp);
        // 카드가 화면보다 길어지지 않게 위아래 여백
        root.setPadding(0, (int) (h * 0.06f), 0, (int) (h * 0.06f));

        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.CENTER;

        try {
            wm.addView(root, lp);
            current = root;
        } catch (Exception e) {
            Prefs.setStatus(c, "팝업 표시 실패: " + e.getMessage());
            return;
        }

        beep(n.urgent);

        if (n.seconds > 0) {
            remaining = n.seconds;
            countdown.setText(remaining + "초 후 자동으로 닫힙니다");
            H.postDelayed(TICK, 1000);
        } else {
            countdown.setText("‘확인’을 누르면 닫힙니다");
        }
    }

    static void dismiss() {
        H.removeCallbacks(TICK);
        if (current != null && wm != null) {
            try { wm.removeView(current); } catch (Exception ignored) {}
        }
        current = null;
        countdown = null;
    }

    private static void beep(boolean urgent) {
        try {
            final ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_MUSIC, 90);
            if (urgent) {
                tg.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 3000);
                H.postDelayed(tg::release, 3500);
            } else {
                tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 500);
                H.postDelayed(tg::release, 1000);
            }
        } catch (Exception ignored) {}
    }
}
