package kr.chungmu.notice;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.VideoView;

import org.json.JSONArray;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 큰 동영상: 여러 조각으로 나뉘어 올라간 파일을 칠판에 내려받아 하나로 합친 뒤 재생한다.
 * 한 번 받은 영상은 칠판에 보관해 두어, 보관함에서 다시 재생할 때는 바로 나온다.
 */
final class PartsPlayer {
    private static final Handler H = new Handler(Looper.getMainLooper());
    private static final int KEEP_FILES = 3;
    private static WindowManager wm;
    private static FrameLayout current;
    private static TextView progress;
    private static VideoView video;
    private static volatile int generation = 0;

    private PartsPlayer() {}

    @SuppressWarnings("deprecation")
    static void play(Context c, JSONArray partsJson, String key, long size, boolean autoClose) {
        final Context app = c.getApplicationContext();
        if (!OverlayPopup.canShow(app)) {
            Prefs.setStatus(app, "동영상을 받았지만 ‘다른 앱 위에 표시’ 권한이 없어 재생하지 못함");
            return;
        }
        VideoOverlay.stop();       // 이 안에서 PartsPlayer.stopOnly()도 불림
        OverlayPopup.dismiss();
        final int myGen = ++generation;

        final List<String> parts = new ArrayList<>();
        for (int i = 0; i < partsJson.length(); i++) {
            String u = partsJson.optString(i, "");
            if (!u.isEmpty()) parts.add(u);
        }
        if (parts.isEmpty()) return;

        wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) return;
        int w = app.getResources().getDisplayMetrics().widthPixels;
        int h = app.getResources().getDisplayMetrics().heightPixels;
        int base = Math.max(w, h);

        FrameLayout root = new FrameLayout(app);
        root.setBackgroundColor(Color.BLACK);

        progress = new TextView(app);
        progress.setTextColor(Color.WHITE);
        progress.setTextSize(TypedValue.COMPLEX_UNIT_PX, base / 45f);
        progress.setGravity(Gravity.CENTER);
        progress.setText("동영상을 준비하는 중입니다...");
        root.addView(progress, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER));

        Button close = new Button(app);
        close.setText("✕  닫기");
        close.setAllCaps(false);
        close.setTextColor(Color.WHITE);
        close.setTextSize(TypedValue.COMPLEX_UNIT_PX, base / 70f);
        GradientDrawable cb = new GradientDrawable();
        cb.setColor(0x99000000);
        cb.setCornerRadius(base / 150f);
        cb.setStroke(Math.max(1, base / 1000), 0x66FFFFFF);
        close.setBackground(cb);
        int pad = base / 120;
        close.setPadding(pad * 2, pad, pad * 2, pad);
        close.setOnClickListener(v -> stopOnly());
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        clp.setMargins(pad, pad, pad, pad);
        root.addView(close, clp);

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
                        | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.OPAQUE);
        lp.gravity = Gravity.CENTER;
        try {
            wm.addView(root, lp);
            current = root;
        } catch (Exception e) {
            Prefs.setStatus(app, "동영상 화면 표시 실패: " + e.getMessage());
            return;
        }

        final File dir = new File(app.getCacheDir(), "videos");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        final File target = new File(dir, key.replaceAll("[^A-Za-z0-9._-]", "_") + ".mp4");
        if (target.exists() && (size <= 0 || target.length() == size)) {
            startVideo(app, target, autoClose, myGen);
            return;
        }

        new Thread(() -> {
            File tmp = new File(dir, target.getName() + ".tmp");
            try {
                long done = 0;
                byte[] buf = new byte[256 * 1024];
                try (OutputStream out = new FileOutputStream(tmp)) {
                    for (int i = 0; i < parts.size(); i++) {
                        HttpURLConnection conn = (HttpURLConnection) new URL(parts.get(i)).openConnection();
                        conn.setConnectTimeout(20000);
                        conn.setReadTimeout(60000);
                        conn.setRequestProperty("User-Agent", "ClassNotice/1.4");
                        int code = conn.getResponseCode();
                        if (code != 200) throw new IOException("조각 " + (i + 1) + " 응답 " + code);
                        try (InputStream in = conn.getInputStream()) {
                            int n;
                            while ((n = in.read(buf)) > 0) {
                                if (generation != myGen) throw new IOException("취소됨");
                                out.write(buf, 0, n);
                                done += n;
                                final long d = done;
                                final int part = i + 1;
                                if (size > 0) {
                                    final int pct = (int) Math.min(100, d * 100 / size);
                                    post(myGen, () -> progress.setText("동영상을 받는 중입니다  " + pct + "%"));
                                } else {
                                    post(myGen, () -> progress.setText("동영상을 받는 중입니다  " + part + "/" + parts.size()
                                            + "  (" + (d / 1048576) + "MB)"));
                                }
                            }
                        } finally {
                            conn.disconnect();
                        }
                    }
                }
                if (size > 0 && tmp.length() != size) throw new IOException("받은 크기가 맞지 않음");
                if (target.exists()) //noinspection ResultOfMethodCallIgnored
                    target.delete();
                if (!tmp.renameTo(target)) throw new IOException("파일 저장 실패");
                cleanCache(dir, target);
                if (generation != myGen) return;
                H.post(() -> startVideo(app, target, autoClose, myGen));
            } catch (Exception e) {
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
                if (generation != myGen) return;
                final String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                Prefs.setStatus(app, "동영상을 받지 못함: " + msg);
                H.post(() -> {
                    if (generation != myGen) return;
                    stopOnly();
                    OverlayPopup.show(app, Notice.info("동영상을 받지 못했습니다",
                            "인터넷 연결을 확인해 주세요. (" + msg + ")", 15));
                });
            }
        }, "video-download").start();
    }

    private static void post(int gen, Runnable r) {
        H.post(() -> { if (generation == gen && progress != null) r.run(); });
    }

    private static void startVideo(Context app, File file, boolean autoClose, int gen) {
        if (generation != gen || current == null) return;
        if (progress != null) progress.setText("");
        video = new VideoView(app);
        current.addView(video, 0, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER));
        video.setOnPreparedListener(mp -> mp.start());
        video.setOnCompletionListener(mp -> {
            if (autoClose) stopOnly();
        });
        video.setOnErrorListener((mp, what, extra) -> {
            stopOnly();
            Prefs.setStatus(app, "동영상 재생 실패 (" + what + ", " + extra + ")");
            OverlayPopup.show(app, Notice.info("동영상을 재생하지 못했습니다",
                    "이 칠판이 재생할 수 없는 형식일 수 있습니다. mp4(H.264) 파일로 바꿔 보세요.", 15));
            return true;
        });
        video.setVideoPath(file.getAbsolutePath());
    }

    /** 최근에 받은 몇 개만 남기고 칠판 저장 공간 정리 */
    private static void cleanCache(File dir, File keep) {
        File[] files = dir.listFiles();
        if (files == null) return;
        Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        int kept = 0;
        for (File f : files) {
            if (f.equals(keep)) { kept++; continue; }
            if (f.getName().endsWith(".tmp")) continue;
            if (kept < KEEP_FILES) { kept++; continue; }
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }

    /** 이 화면만 닫기 (VideoOverlay.stop()에서 불림) */
    static void stopOnly() {
        generation++;
        if (video != null) {
            try { video.stopPlayback(); } catch (Exception ignored) {}
            video = null;
        }
        if (current != null && wm != null) {
            try { wm.removeView(current); } catch (Exception ignored) {}
        }
        current = null;
        progress = null;
    }
}
