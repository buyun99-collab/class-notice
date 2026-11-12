package kr.chungmu.notice;

import android.content.Context;
import android.content.SharedPreferences;

/** 칠판 한 대의 설정값 저장소 */
final class Prefs {
    static final String DEFAULT_SERVER = "https://ntfy.sh";

    private Prefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("cfg", Context.MODE_PRIVATE);
    }

    static String server(Context c) {
        String s = sp(c).getString("server", DEFAULT_SERVER).trim();
        return s.isEmpty() ? DEFAULT_SERVER : s;
    }

    static String topic(Context c) { return sp(c).getString("topic", "").trim(); }
    static String room(Context c) { return sp(c).getString("room", "").trim(); }
    static String pin(Context c) { return sp(c).getString("pin", ""); }
    static String lastId(Context c) { return sp(c).getString("lastId", ""); }
    static String status(Context c) { return sp(c).getString("status", "아직 시작하지 않음"); }

    static void save(Context c, String server, String topic, String room, String pin) {
        SharedPreferences.Editor e = sp(c).edit();
        if (!topic.equals(topic(c)) || !server.equals(server(c))) {
            e.putString("lastId", "");   // 채널이 바뀌면 이전 기록은 의미 없음
        }
        e.putString("server", server)
         .putString("topic", topic)
         .putString("room", room)
         .putString("pin", pin)
         .apply();
    }

    /** 칠판마다 한 번 만들어지는 고유 번호 (예: K7Q2). 관리자 페이지에서 칠판을 구분할 때 씀 */
    static synchronized String devId(Context c) {
        String id = sp(c).getString("devId", "");
        if (id.isEmpty()) {
            String abc = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
            java.security.SecureRandom r = new java.security.SecureRandom();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 4; i++) sb.append(abc.charAt(r.nextInt(abc.length())));
            id = sb.toString();
            sp(c).edit().putString("devId", id).apply();
        }
        return id;
    }

    static void setRoom(Context c, String room) { sp(c).edit().putString("room", room.trim()).apply(); }

    static boolean forceWake(Context c) { return sp(c).getBoolean("forceWake", false); }
    static void setForceWake(Context c, boolean v) { sp(c).edit().putBoolean("forceWake", v).apply(); }

    static String lastEvent(Context c) { return sp(c).getString("lastEvent", "없음"); }
    static void setLastEvent(Context c, String s) {
        String t = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.KOREA).format(new java.util.Date());
        sp(c).edit().putString("lastEvent", t + "  " + s).apply();
    }

    static void setLastId(Context c, String id) { sp(c).edit().putString("lastId", id).apply(); }
    static void setStatus(Context c, String s) { sp(c).edit().putString("status", s).apply(); }
}
