package kr.chungmu.notice;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;

/** 다른 앱 위에 화면 전체로 동영상을 재생 (유튜브 또는 동영상 파일 주소) */
final class VideoOverlay {
    private static final Handler H = new Handler(Looper.getMainLooper());
    private static WindowManager wm;
    private static FrameLayout current;
    private static WebView web;

    private VideoOverlay() {}

    /** 유튜브 영상 ID나 주소에서 ID만 뽑기 */
    static String youtubeId(String s) {
        if (s == null) return "";
        s = s.trim();
        if (s.matches("[A-Za-z0-9_-]{11}")) return s;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?:v=|youtu\\.be/|/shorts/|/embed/|/live/)([A-Za-z0-9_-]{11})")
                .matcher(s);
        return m.find() ? m.group(1) : "";
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @SuppressWarnings("deprecation")
    static void play(Context c, String ytId, String src, boolean autoClose) {
        if (!OverlayPopup.canShow(c)) {
            Prefs.setStatus(c, "동영상을 받았지만 ‘다른 앱 위에 표시’ 권한이 없어 재생하지 못함");
            return;
        }
        stop();
        OverlayPopup.dismiss();
        final Context app = c.getApplicationContext();
        wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) return;

        int w = app.getResources().getDisplayMetrics().widthPixels;
        int h = app.getResources().getDisplayMetrics().heightPixels;
        int base = Math.max(w, h);

        FrameLayout root = new FrameLayout(app);
        root.setBackgroundColor(Color.BLACK);

        web = new WebView(app);
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setLoadWithOverviewMode(true);
        ws.setUseWideViewPort(true);
        web.setBackgroundColor(Color.BLACK);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new Bridge(app, ytId, autoClose), "Board");
        root.addView(web, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // 오른쪽 위 닫기 버튼
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
        close.setOnClickListener(v -> stop());
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        clp.setMargins(pad, pad, pad, pad);
        root.addView(close, clp);

        String html;
        if (!ytId.isEmpty()) {
            html = "<!DOCTYPE html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>"
                    + "<style>html,body{margin:0;height:100%;background:#000;overflow:hidden}#p{width:100%;height:100%}</style>"
                    + "</head><body><div id='p'></div>"
                    + "<script>"
                    + "var tag=document.createElement('script');tag.src='https://www.youtube.com/iframe_api';document.head.appendChild(tag);"
                    + "var player;function onYouTubeIframeAPIReady(){player=new YT.Player('p',{width:'100%',height:'100%',videoId:'" + ytId + "',"
                    + "playerVars:{autoplay:1,playsinline:1,rel:0,controls:1,fs:0,modestbranding:1},"
                    + "events:{onReady:function(e){e.target.unMute();e.target.setVolume(100);e.target.playVideo();},"
                    + "onStateChange:function(e){if(e.data===0)Board.ended();},"
                    + "onError:function(e){Board.error(e.data);}}});}"
                    + "</script></body></html>";
        } else {
            html = "<!DOCTYPE html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>"
                    + "<style>html,body{margin:0;height:100%;background:#000;overflow:hidden}video{width:100%;height:100%;object-fit:contain}</style>"
                    + "</head><body><video id='v' src=\"" + esc(src) + "\" autoplay controls playsinline></video>"
                    + "<script>var v=document.getElementById('v');v.onended=function(){Board.ended();};"
                    + "v.onerror=function(){Board.error(-1);};v.play().catch(function(){});</script></body></html>";
        }
        // 실제 https 주소처럼 보이게 해야 유튜브 퍼가기 재생이 허용됨
        web.loadDataWithBaseURL("https://classnotice.app/player", html, "text/html", "utf-8", null);

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
            web.destroy();
            web = null;
        }
    }

    static void stop() {
        if (web != null) {
            try {
                web.loadUrl("about:blank");
                web.stopLoading();
                web.destroy();
            } catch (Exception ignored) {}
            web = null;
        }
        if (current != null && wm != null) {
            try { wm.removeView(current); } catch (Exception ignored) {}
        }
        current = null;
    }

    static boolean isPlaying() { return current != null; }

    /** 웹 화면의 재생기에서 앱으로 신호를 보내는 다리 */
    private static final class Bridge {
        private final Context app;
        private final String ytId;
        private final boolean autoClose;

        Bridge(Context app, String ytId, boolean autoClose) {
            this.app = app;
            this.ytId = ytId;
            this.autoClose = autoClose;
        }

        @JavascriptInterface
        public void ended() {
            if (autoClose) H.post(VideoOverlay::stop);
        }

        @JavascriptInterface
        public void error(int code) {
            H.post(() -> {
                stop();
                if (!ytId.isEmpty()) {
                    // 퍼가기가 막힌 영상(101, 150, 153 등)은 유튜브 앱이나 브라우저로 직접 열기
                    try {
                        Intent i = new Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://www.youtube.com/watch?v=" + ytId));
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        app.startActivity(i);
                        Prefs.setStatus(app, "퍼가기가 막힌 영상이라 유튜브로 열었음 (오류 " + code + ")");
                    } catch (Exception e) {
                        Prefs.setStatus(app, "동영상 재생 실패 (오류 " + code + ")");
                    }
                } else {
                    Prefs.setStatus(app, "동영상 파일을 재생하지 못함: 주소를 확인하세요");
                    OverlayPopup.show(app, Notice.info("동영상을 재생하지 못했습니다",
                            "동영상 주소가 맞는지, 이 칠판에서 열 수 있는 주소인지 확인해 주세요.", 15));
                }
            });
        }
    }
}
