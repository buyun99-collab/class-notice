package kr.chungmu.notice;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 백그라운드에서 ntfy 채널에 계속 연결해 두고, 공지가 오면 화면 위 팝업을 띄운다.
 */
public class NoticeService extends Service {
    private static final String CHANNEL = "notice_service";
    /** 연결이 끊겼다 다시 붙을 때, 이보다 오래된 공지는 띄우지 않음 (초) */
    private static final long MAX_AGE_SEC = 10 * 60;

    private volatile boolean running;
    private volatile HttpURLConnection conn;
    private Thread worker;
    private Handler main;
    private PowerManager.WakeLock cpuLock;
    private WifiManager.WifiLock wifiLock;

    static void start(Context c) {
        Intent i = new Intent(c, NoticeService.class);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
        else c.startService(i);
    }

    static void restart(Context c) {
        c.stopService(new Intent(c, NoticeService.class));
        new Handler(Looper.getMainLooper()).postDelayed(() -> start(c), 800);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        main = new Handler(Looper.getMainLooper());
        goForeground();

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null) {
            cpuLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ClassNotice:cpu");
            cpuLock.setReferenceCounted(false);
            cpuLock.acquire();
        }
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        if (wm != null) {
            try {
                wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "ClassNotice:wifi");
                wifiLock.setReferenceCounted(false);
                wifiLock.acquire();
            } catch (Exception ignored) {}
        }

        running = true;
        worker = new Thread(this::loop, "ntfy-listener");
        worker.start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        HttpURLConnection c = conn;
        if (c != null) {
            new Thread(c::disconnect).start();
        }
        if (worker != null) worker.interrupt();
        try { if (cpuLock != null && cpuLock.isHeld()) cpuLock.release(); } catch (Exception ignored) {}
        try { if (wifiLock != null && wifiLock.isHeld()) wifiLock.release(); } catch (Exception ignored) {}
        Prefs.setStatus(this, "중지됨");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    // ---------------------------------------------------------------

    private void goForeground() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null && nm.getNotificationChannel(CHANNEL) == null) {
                NotificationChannel ch = new NotificationChannel(
                        CHANNEL, "공지 대기", NotificationManager.IMPORTANCE_LOW);
                ch.setShowBadge(false);
                nm.createNotificationChannel(ch);
            }
        }
        int piFlags = Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0;
        PendingIntent pi = PendingIntent.getActivity(
                this, 0, new Intent(this, MainActivity.class), piFlags);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        String room = Prefs.room(this);
        Notification n = b.setContentTitle("교실 공지 알림 대기 중")
                .setContentText(room.isEmpty() ? "반 이름 미설정" : room)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setOngoing(true)
                .setContentIntent(pi)
                .build();

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(1, n);
        }
    }

    private void status(String s) {
        String t = new SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(new Date());
        Prefs.setStatus(this, s + " (" + t + ")");
    }

    private void loop() {
        long backoff = 2000;
        while (running) {
            String topic = Prefs.topic(this);
            if (topic.isEmpty()) {
                status("채널 이름이 설정되지 않음");
                sleep(5000);
                continue;
            }
            HttpURLConnection c = null;
            try {
                String base = Prefs.server(this).replaceAll("/+$", "");
                String last = Prefs.lastId(this);
                String url = base + "/" + URLEncoder.encode(topic, "UTF-8") + "/json"
                        + (last.isEmpty() ? "" : "?since=" + URLEncoder.encode(last, "UTF-8"));

                c = (HttpURLConnection) new URL(url).openConnection();
                conn = c;
                c.setConnectTimeout(15000);
                c.setReadTimeout(130000);   // ntfy는 약 45초마다 keepalive를 보냄
                c.setRequestProperty("User-Agent", "ClassNotice/1.0");
                int code = c.getResponseCode();
                if (code != 200) throw new IOException("서버 응답 " + code);

                status("연결됨");
                backoff = 2000;
                BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
                String line;
                while (running && (line = r.readLine()) != null) {
                    handleLine(line);
                }
                if (running) status("서버가 연결을 닫음, 다시 연결 중");
            } catch (Exception e) {
                if (!running) break;
                String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                status("연결 끊김, 다시 연결 중: " + msg);
            } finally {
                if (c != null) {
                    try { c.disconnect(); } catch (Exception ignored) {}
                }
                conn = null;
            }
            sleep(backoff);
            backoff = Math.min(backoff * 2, 60000);
        }
    }

    private void handleLine(String line) {
        try {
            JSONObject o = new JSONObject(line);
            String ev = o.optString("event");
            if ("open".equals(ev)) {
                status("연결됨");
                report();   // 연결될 때마다 관리자 페이지에 내 번호와 반 이름을 알림
                return;
            }
            if ("keepalive".equals(ev)) {
                status("연결됨");
                return;
            }
            if (!"message".equals(ev)) return;

            String id = o.optString("id", "");
            if (!id.isEmpty()) {
                if (id.equals(Prefs.lastId(this))) return;
                Prefs.setLastId(this, id);
            }
            long time = o.optLong("time", 0);
            if (time > 0 && System.currentTimeMillis() / 1000 - time > MAX_AGE_SEC) return;

            final Notice n = Notice.parse(o);
            if (n.cmd != null) {
                handleCommand(n, o.optString("message", ""));
                return;
            }
            if (!n.matches(Prefs.room(this))) return;

            wakeScreen(this, "공지");
            main.post(() -> OverlayPopup.show(getApplicationContext(), n));
        } catch (Exception ignored) {
            // 형식이 맞지 않는 줄은 무시
        }
    }

    // ---------------- 관리 명령 ----------------

    private void handleCommand(Notice n, String message) {
        String cmd = n.cmd;
        String me = Prefs.devId(this);
        JSONObject arg;
        try { arg = new JSONObject(message); } catch (Exception e) { arg = new JSONObject(); }

        switch (cmd) {
            case "close":
                main.post(OverlayPopup::dismiss);
                break;

            case "video": {
                if (!n.matches(Prefs.room(this))) return;
                final org.json.JSONArray parts = arg.optJSONArray("parts");
                if (parts != null && parts.length() > 0) {
                    final String key = arg.optString("key", "video");
                    final long size = arg.optLong("size", 0);
                    final boolean ac = arg.optBoolean("autoClose", true);
                    wakeScreen(this, "동영상");
                    main.post(() -> PartsPlayer.play(getApplicationContext(), parts, key, size, ac));
                    break;
                }
                final String yt = VideoOverlay.youtubeId(arg.optString("yt", ""));
                final String src = arg.optString("src", "").trim();
                if (yt.isEmpty() && src.isEmpty()) return;
                final boolean autoClose = arg.optBoolean("autoClose", true);
                wakeScreen(this, "동영상");
                main.post(() -> VideoOverlay.play(getApplicationContext(), yt, src, autoClose));
                break;
            }

            case "videostop":
                if (!n.matches(Prefs.room(this))) return;
                main.post(VideoOverlay::stop);
                break;

            case "ping":
                report();
                break;

            case "setroom": {
                if (!me.equalsIgnoreCase(arg.optString("dev", ""))) return;
                changeRoom(arg.optString("room", ""));
                break;
            }

            case "rename": {
                JSONObject map = arg.optJSONObject("map");
                if (map == null) return;
                String mine = Notice.norm(Prefs.room(this));
                if (mine.isEmpty()) return;
                java.util.Iterator<String> keys = map.keys();
                while (keys.hasNext()) {
                    String from = keys.next();
                    if (Notice.norm(from).equals(mine)) {
                        changeRoom(map.optString(from, ""));
                        break;
                    }
                }
                break;
            }

            case "identify": {
                String dev = arg.optString("dev", "");
                if (!dev.equalsIgnoreCase(me) && !dev.equalsIgnoreCase("all")) return;
                final Notice info = Notice.info("칠판 번호  " + me,
                        "반 이름: " + roomLabel(Prefs.room(this)), 30);
                wakeScreen(this, "칠판 번호 확인");
                main.post(() -> OverlayPopup.show(getApplicationContext(), info));
                break;
            }
        }
    }

    private static String roomLabel(String room) {
        return room.isEmpty() ? "(정해지지 않음)" : room;
    }

    private void changeRoom(String newRoom) {
        newRoom = newRoom.trim();
        String old = Prefs.room(this);
        if (newRoom.equals(old)) { report(); return; }
        Prefs.setRoom(this, newRoom);
        report();
        final Notice n = Notice.info("반 이름이 바뀌었습니다",
                roomLabel(old) + "  →  " + roomLabel(newRoom), 15);
        main.post(() -> {
            goForeground();   // 알림 표시줄의 반 이름도 갱신
            OverlayPopup.show(getApplicationContext(), n);
        });
    }

    /** 상태 채널(채널이름-status)에 내 번호, 반 이름 등을 보냄 */
    private void report() {
        final Context ctx = getApplicationContext();
        new Thread(() -> {
            HttpURLConnection c = null;
            try {
                JSONObject st = new JSONObject();
                st.put("dev", Prefs.devId(ctx));
                st.put("room", Prefs.room(ctx));
                st.put("ver", "1.4");
                st.put("overlay", OverlayPopup.canShow(ctx));
                st.put("model", Build.MANUFACTURER + " " + Build.MODEL);

                JSONObject body = new JSONObject();
                body.put("topic", Prefs.topic(ctx) + "-status");
                body.put("message", st.toString());
                body.put("tags", new org.json.JSONArray().put("status"));

                c = (HttpURLConnection) new URL(Prefs.server(ctx).replaceAll("/+$", "") + "/").openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(15000);
                c.setDoOutput(true);
                c.setRequestMethod("POST");
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                byte[] bytes = body.toString().getBytes("UTF-8");
                c.getOutputStream().write(bytes);
                c.getOutputStream().close();
                c.getResponseCode();
            } catch (Exception ignored) {
            } finally {
                if (c != null) try { c.disconnect(); } catch (Exception ignored) {}
            }
        }, "status-report").start();
    }

    /**
     * 화면을 켠다. 안드로이드 수준으로 화면이 꺼져 있으면 투명 화면(WakeActivity)을 띄워 켜고,
     * 받을 때 화면이 어떤 상태였는지 기록해 둔다(문제 확인용).
     */
    @SuppressWarnings("deprecation")
    static void wakeScreen(Context ctx, String what) {
        boolean interactive = true;
        try {
            PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                interactive = Build.VERSION.SDK_INT >= 20 ? pm.isInteractive() : pm.isScreenOn();
                PowerManager.WakeLock wl = pm.newWakeLock(
                        PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP
                                | PowerManager.ON_AFTER_RELEASE,
                        "ClassNotice:screen");
                wl.acquire(20000);
            }
        } catch (Exception ignored) {}

        boolean force = Prefs.forceWake(ctx);
        if (!interactive || force) {
            WakeActivity.wake(ctx.getApplicationContext());
        }
        Prefs.setLastEvent(ctx, what + " 받음 / 받을 때 화면: "
                + (interactive ? "켜짐(안드로이드 기준)" : "꺼짐 → 화면 켜기 시도")
                + (force && interactive ? " / 강하게 깨우기 사용" : ""));
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}
