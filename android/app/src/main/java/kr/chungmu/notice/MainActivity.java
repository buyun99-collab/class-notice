package kr.chungmu.notice;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** 칠판에서 한 번만 설정하는 화면 */
public class MainActivity extends Activity {
    private final Handler h = new Handler(Looper.getMainLooper());
    private TextView statusView, permView, eventView;
    private EditText serverIn, topicIn, roomIn, pinIn;
    private boolean uiReady = false;
    private String lastSeenRoom = null;

    private final Runnable refresher = new Runnable() {
        @Override public void run() {
            refresh();
            h.postDelayed(this, 2000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Prefs.pin(this).isEmpty()) {
            buildUi();
        } else {
            askPin();
        }
        if (!Prefs.topic(this).isEmpty()) NoticeService.start(this);
    }

    private void askPin() {
        final EditText in = new EditText(this);
        in.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        new AlertDialog.Builder(this)
                .setTitle("설정 잠금")
                .setMessage("관리 PIN을 입력하세요.")
                .setView(in)
                .setCancelable(false)
                .setPositiveButton("확인", (d, w) -> {
                    if (in.getText().toString().equals(Prefs.pin(this))) buildUi();
                    else { Toast.makeText(this, "PIN이 틀렸습니다", Toast.LENGTH_SHORT).show(); finish(); }
                })
                .setNegativeButton("닫기", (d, w) -> finish())
                .show();
    }

    private TextView label(LinearLayout parent, String text, float sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(14), 0, dp(4));
        parent.addView(t);
        return t;
    }

    private EditText input(LinearLayout parent, String value, String hint, int type) {
        EditText e = new EditText(this);
        e.setText(value);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setInputType(type);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        parent.addView(e);
        return e;
    }

    private Button button(LinearLayout parent, String text, Runnable action) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        b.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        parent.addView(b, lp);
        return b;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private void buildUi() {
        ScrollView sv = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(24), dp(16), dp(24), dp(32));
        sv.addView(col);

        label(col, "교실 공지 알림 설정", 26, true);
        String ver = "";
        try { ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception ignored) {}
        TextView devView = label(col, "이 칠판 번호: " + Prefs.devId(this) + "   앱 버전: " + ver
                + "   (관리자 페이지 ‘칠판 관리’에서 이 번호로 구분합니다)", 16, true);
        devView.setTextColor(Color.rgb(46, 125, 50));
        statusView = label(col, "", 16, false);
        statusView.setTextColor(Color.rgb(21, 101, 192));
        permView = label(col, "", 15, false);

        label(col, "반 이름 (예: 1-3, 교무실, 강당)", 16, true);
        roomIn = input(col, Prefs.room(this), "1-3", InputType.TYPE_CLASS_TEXT);

        label(col, "채널 이름 (관리자 페이지와 똑같이)", 16, true);
        topicIn = input(col, Prefs.topic(this), "영문, 숫자, - _ 만 사용",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);

        label(col, "서버 주소 (보통 그대로 두세요)", 16, true);
        serverIn = input(col, Prefs.server(this), Prefs.DEFAULT_SERVER,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);

        label(col, "설정 잠금 PIN (숫자, 비워두면 잠금 없음)", 16, true);
        pinIn = input(col, Prefs.pin(this), "예: 1234",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);

        button(col, "저장하고 대기 시작", this::save);
        button(col, "① ‘다른 앱 위에 표시’ 권한 허용하기", this::openOverlaySetting);
        button(col, "② 배터리 최적화에서 제외하기", this::openBatterySetting);
        button(col, "테스트 팝업 띄워 보기", () ->
                OverlayPopup.show(getApplicationContext(), Notice.test(Prefs.room(this))));

        button(col, "화면 꺼짐 시험: 누르고 10초 안에 칠판 화면을 끄세요", () -> {
            Toast.makeText(this, "10초 뒤 팝업을 띄웁니다. 지금 칠판 화면을 대기 상태로 만드세요.", Toast.LENGTH_LONG).show();
            final android.content.Context app = getApplicationContext();
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                NoticeService.wakeScreen(app, "화면 꺼짐 시험");
                OverlayPopup.show(app, Notice.info("화면 꺼짐 시험", "대기 상태에서 이 화면이 보이면 성공입니다.", 30));
            }, 10000);
        });

        android.widget.CheckBox force = new android.widget.CheckBox(this);
        force.setText("강하게 깨우기 (대기 화면에서 공지가 안 보일 때만 켜세요)");
        force.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        force.setChecked(Prefs.forceWake(this));
        force.setOnCheckedChangeListener((b, v) -> Prefs.setForceWake(this, v));
        col.addView(force);

        eventView = label(col, "", 14, false);
        eventView.setTextColor(Color.DKGRAY);

        TextView help = label(col,
                "설정이 끝나면 이 화면을 닫아도 됩니다. 칠판을 껐다 켜도 자동으로 다시 대기합니다.",
                14, false);
        help.setTextColor(Color.GRAY);

        setContentView(sv);
        uiReady = true;
        refresh();

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }
    }

    private void save() {
        String topic = topicIn.getText().toString().trim();
        String room = roomIn.getText().toString().trim();
        String server = serverIn.getText().toString().trim();
        String pin = pinIn.getText().toString().trim();
        if (!topic.matches("[A-Za-z0-9_-]{8,56}")) {
            Toast.makeText(this, "채널 이름은 영문, 숫자, -, _ 로 8~56자여야 합니다", Toast.LENGTH_LONG).show();
            return;
        }
        if (server.isEmpty()) server = Prefs.DEFAULT_SERVER;
        if (!server.startsWith("http://") && !server.startsWith("https://")) server = "https://" + server;
        Prefs.save(this, server, topic, room, pin);
        NoticeService.restart(this);
        Toast.makeText(this, "저장했습니다. 대기를 시작합니다.", Toast.LENGTH_SHORT).show();
        refresh();
    }

    private void openOverlaySetting() {
        if (Build.VERSION.SDK_INT < 23) {
            Toast.makeText(this, "이 기기는 별도 허용이 필요 없습니다", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            openAppDetails();
        }
    }

    private void openBatterySetting() {
        if (Build.VERSION.SDK_INT < 23) {
            Toast.makeText(this, "이 기기는 별도 설정이 필요 없습니다", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            openAppDetails();
        }
    }

    private void openAppDetails() {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            Toast.makeText(this, "설정 화면을 열 수 없습니다. 안내서의 adb 방법을 참고하세요.", Toast.LENGTH_LONG).show();
        }
    }

    private void refresh() {
        if (!uiReady) return;
        statusView.setText("상태: " + Prefs.status(this));
        eventView.setText("마지막으로 받은 것: " + Prefs.lastEvent(this));
        // 관리자 페이지에서 반 이름을 원격으로 바꿨을 수 있으니, 입력 중이 아닐 때 화면에 반영
        String room = Prefs.room(this);
        if (lastSeenRoom != null && !room.equals(lastSeenRoom)) roomIn.setText(room);
        lastSeenRoom = room;
        StringBuilder sb = new StringBuilder();
        sb.append(OverlayPopup.canShow(this) ? "✔ 다른 앱 위에 표시: 허용됨" : "✘ 다른 앱 위에 표시: 허용 필요 (①번 버튼)");
        if (Build.VERSION.SDK_INT >= 23) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            boolean ok = pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
            sb.append("\n").append(ok ? "✔ 배터리 최적화: 제외됨" : "△ 배터리 최적화: 제외 권장 (②번 버튼)");
        }
        permView.setText(sb.toString());
    }

    @Override
    protected void onResume() {
        super.onResume();
        h.post(refresher);
    }

    @Override
    protected void onPause() {
        h.removeCallbacks(refresher);
        super.onPause();
    }
}
