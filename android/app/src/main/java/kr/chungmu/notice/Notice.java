package kr.chungmu.notice;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 공지 한 건.
 *
 * ntfy 메시지 형식
 *   title    : 제목
 *   message  : 내용 (맨 앞에 [1-3,2-*] 처럼 대상을 쓸 수도 있음)
 *   priority : 4 이상이면 긴급
 *   tags     : "to:1-3,2-*"  대상 반 (없으면 전체)
 *              "sec:60"      표시 시간(초), 0이면 닫을 때까지
 *              "urgent"      긴급
 *              "cmd:close"   떠 있는 공지를 모두 닫기
 */
final class Notice {
    String title = "";
    String body = "";
    boolean urgent = false;
    int seconds = 60;
    boolean close = false;
    final List<String> targets = new ArrayList<>();

    private static final Pattern PREFIX = Pattern.compile("^\\s*\\[([^\\]]+)\\]\\s*");

    static Notice parse(JSONObject o) {
        Notice n = new Notice();
        n.title = o.optString("title", "").trim();
        n.body = o.optString("message", "");
        n.urgent = o.optInt("priority", 3) >= 4;

        JSONArray tags = o.optJSONArray("tags");
        if (tags != null) {
            for (int i = 0; i < tags.length(); i++) {
                String t = tags.optString(i, "").trim();
                String lower = t.toLowerCase(Locale.ROOT);
                if (lower.startsWith("to:")) {
                    addTargets(n, t.substring(3));
                } else if (lower.startsWith("sec:")) {
                    try { n.seconds = Math.max(0, Integer.parseInt(t.substring(4).trim())); }
                    catch (NumberFormatException ignored) {}
                } else if (lower.equals("urgent") || lower.equals("긴급")) {
                    n.urgent = true;
                } else if (lower.equals("cmd:close")) {
                    n.close = true;
                }
            }
        }

        Matcher m = PREFIX.matcher(n.body);
        if (m.find()) {
            addTargets(n, m.group(1));
            n.body = n.body.substring(m.end());
        }

        // ntfy가 내용 없는 메시지에 붙이는 기본 문구는 지움
        if (n.body.equals("triggered")) n.body = "";
        if (n.title.isEmpty()) n.title = n.urgent ? "긴급 공지" : "공지";
        return n;
    }

    static Notice test(String room) {
        Notice n = new Notice();
        n.title = "테스트 공지";
        n.body = "이 칠판(" + (room.isEmpty() ? "반 미지정" : room) + ")에 공지가 이렇게 표시됩니다.";
        n.seconds = 15;
        return n;
    }

    private static void addTargets(Notice n, String csv) {
        for (String s : csv.split("[,，\\s]+")) {
            if (!s.trim().isEmpty()) n.targets.add(s.trim());
        }
    }

    private static String norm(String s) {
        return s.replaceAll("\\s+", "").replace("반", "").toLowerCase(Locale.ROOT);
    }

    /** 이 칠판(room)이 받아야 하는 공지인지 */
    boolean matches(String room) {
        if (targets.isEmpty()) return true;
        String r = norm(room);
        for (String raw : targets) {
            String t = norm(raw);
            if (t.equals("all") || t.equals("전체")) return true;
            if (r.isEmpty()) continue;
            if (t.equals(r)) return true;
            if (t.endsWith("-*") && r.startsWith(t.substring(0, t.length() - 1))) return true;
            if (t.endsWith("학년") && r.startsWith(t.substring(0, t.length() - 2) + "-")) return true;
        }
        return false;
    }
}
