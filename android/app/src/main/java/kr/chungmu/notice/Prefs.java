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

    static void setLastId(Context c, String id) { sp(c).edit().putString("lastId", id).apply(); }
    static void setStatus(Context c, String s) { sp(c).edit().putString("status", s).apply(); }
}
