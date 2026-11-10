package com.aliboder.classwidget;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/**
 * 极简 ICS 解析：只取课表场景需要的 VEVENT 字段。
 *
 * 支持：SUMMARY / LOCATION / DESCRIPTION / DTSTART / DTEND / DURATION /
 *       RRULE(FREQ=WEEKLY, INTERVAL, COUNT, UNTIL, BYDAY) / EXDATE /
 *       TZID / 末尾 Z(UTC) / VALUE=DATE(全天)。
 * 不支持：RECURRENCE-ID 例外实例（直接丢弃）、BYMONTH 等复杂重复规则。
 */
public final class IcsParser {

    private static final int MAX_EVENTS = 5000;
    private static final int MAX_OCCURRENCES = 500;
    private static final long HORIZON_MS = 400L * 24L * 3600L * 1000L;
    private static final long DAY_MS = 24L * 3600L * 1000L;

    private IcsParser() {
    }

    /** 直接从文本解析（配对码拉回来的是字符串，不经过文件系统） */
    public static List<Event> parse(String text) throws IOException {
        return parse(new java.io.ByteArrayInputStream(
                text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    public static List<Event> parse(InputStream in) throws IOException {
        List<String> lines = unfold(in);
        List<Event> result = new ArrayList<>();
        List<String> block = null;
        for (String line : lines) {
            String up = line.toUpperCase(Locale.US);
            if (up.equals("BEGIN:VEVENT")) {
                block = new ArrayList<>();
            } else if (up.equals("END:VEVENT")) {
                if (block != null) {
                    parseVEvent(block, result);
                    block = null;
                }
            } else if (block != null) {
                block.add(line);
            }
            if (result.size() >= MAX_EVENTS) {
                break;
            }
        }
        Collections.sort(result, new Comparator<Event>() {
            @Override
            public int compare(Event a, Event b) {
                return Long.compare(a.start, b.start);
            }
        });
        return result;
    }

    /* ---------------- 折行还原 ---------------- */

    private static List<String> unfold(InputStream in) throws IOException {
        List<String> out = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        StringBuilder cur = null;
        String raw;
        while ((raw = reader.readLine()) != null) {
            if (cur != null && (raw.startsWith(" ") || raw.startsWith("\t"))) {
                cur.append(raw.substring(1));
                continue;
            }
            if (cur != null) {
                out.add(cur.toString());
            }
            cur = new StringBuilder(raw);
        }
        if (cur != null) {
            out.add(cur.toString());
        }
        reader.close();
        return out;
    }

    /* ---------------- 属性取值 ---------------- */

    private static String nameOf(String line) {
        int colon = line.indexOf(':');
        String head = colon < 0 ? line : line.substring(0, colon);
        int semi = head.indexOf(';');
        return (semi < 0 ? head : head.substring(0, semi)).trim().toUpperCase(Locale.US);
    }

    private static String valueOf(String line) {
        int colon = line.indexOf(':');
        return colon < 0 ? "" : line.substring(colon + 1);
    }

    private static String paramOf(String line, String param) {
        if (line == null) {
            return null;
        }
        int colon = line.indexOf(':');
        String head = colon < 0 ? line : line.substring(0, colon);
        String[] parts = head.split(";");
        for (int i = 1; i < parts.length; i++) {
            String p = parts[i];
            int eq = p.indexOf('=');
            if (eq > 0 && p.substring(0, eq).trim().equalsIgnoreCase(param)) {
                String v = p.substring(eq + 1).trim();
                if (v.length() > 1 && v.startsWith("\"") && v.endsWith("\"")) {
                    v = v.substring(1, v.length() - 1);
                }
                return v;
            }
        }
        return null;
    }

    private static String unescape(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(i + 1);
                if (n == 'n' || n == 'N') {
                    b.append('\n');
                    i++;
                } else if (n == ',' || n == ';' || n == '\\') {
                    b.append(n);
                    i++;
                } else {
                    b.append(c);
                }
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }

    /* ---------------- 单条事件 ---------------- */

    private static void parseVEvent(List<String> props, List<Event> out) {
        String title = null;
        String place = "";
        String teacher = "";
        String desc = "";
        String kind = "";
        String startRaw = null;
        String startLine = null;
        String endRaw = null;
        String endLine = null;
        String durRaw = null;
        String rrule = null;
        String organizer = null;
        String attendee = null;
        String status = null;
        boolean hasOverride = false;
        List<String> exdates = new ArrayList<>();

        int depth = 0;
        for (String line : props) {
            String upper = line.toUpperCase(Locale.US);
            if (upper.startsWith("BEGIN:")) {
                depth++;
                continue;
            }
            if (upper.startsWith("END:")) {
                depth--;
                continue;
            }
            if (depth > 0) {
                // ⛔ VALARM 里也有 DESCRIPTION（提醒文案），同名字段会覆盖掉正文，
                //    之前教师就是这样丢的（实测 237 条全变"课名（教室）"）。
                continue;
            }
            String name = nameOf(line);
            if ("SUMMARY".equals(name)) {
                title = unescape(valueOf(line));
            } else if ("LOCATION".equals(name)) {
                place = unescape(valueOf(line));
            } else if ("DESCRIPTION".equals(name)) {
                desc = unescape(valueOf(line));
            } else if ("DTSTART".equals(name)) {
                startRaw = valueOf(line).trim();
                startLine = line;
            } else if ("DTEND".equals(name)) {
                endRaw = valueOf(line).trim();
                endLine = line;
            } else if ("DURATION".equals(name)) {
                durRaw = valueOf(line).trim();
            } else if ("ORGANIZER".equals(name)) {
                organizer = line;
            } else if ("ATTENDEE".equals(name)) {
                attendee = line;
            } else if ("RRULE".equals(name)) {
                rrule = valueOf(line).trim();
            } else if ("EXDATE".equals(name)) {
                exdates.add(valueOf(line).trim());
            } else if ("RECURRENCE-ID".equals(name)) {
                hasOverride = true;
            } else if ("STATUS".equals(name)) {
                status = valueOf(line).trim();
            } else if ("CATEGORIES".equals(name)) {
                // 2026-11-10：导出方写「课程 / 考试 / 日程」，导入后按类型显示标签。
                // 老文件没有这行 → 保持默认「课程」（和以前表现一致）。
                String v = unescape(valueOf(line)).trim();
                if (!v.isEmpty()) {
                    kind = v;
                }
            }
        }

        if (status != null && status.toUpperCase(Locale.US).contains("CANCELLED")) {
            return;
        }
        if (startRaw == null || startRaw.isEmpty() || hasOverride) {
            return;
        }

        long start = parseDateMillis(startRaw, startLine);
        if (start <= 0) {
            return;
        }
        boolean allDay = isAllDay(startRaw, startLine);

        long end = 0;
        if (endRaw != null && !endRaw.isEmpty()) {
            end = parseDateMillis(endRaw, endLine);
        }
        if (end <= 0 && durRaw != null && !durRaw.isEmpty()) {
            end = start + parseDuration(durRaw);
        }
        if (end <= start) {
            end = start + (allDay ? DAY_MS : 45L * 60000L);
        }

        // 诊断：把原始 DESCRIPTION 留下来（换行转成 ⏎ 便于单行展示）
        String rawDesc = desc == null ? "" : desc.replace("\n", " / ");

        if (title == null || title.trim().isEmpty()) {
            title = "日程";
        }
        if (place.trim().isEmpty()) {
            place = extractPlace(desc);
        }
        teacher = extractTeacher(desc, organizer, attendee);

        if (rrule == null || rrule.isEmpty()) {
            out.add(make(title, place, teacher, rawDesc, start, end, allDay, kind));
            return;
        }

        Set<Long> skip = new HashSet<>();
        for (String ex : exdates) {
            for (String one : ex.split(",")) {
                long t = parseDateMillis(one.trim(), startLine);
                if (t > 0) {
                    skip.add(t);
                }
            }
        }
        long duration = end - start;
        for (Long t : expandWeekly(start, rrule, startLine)) {
            if (skip.contains(t)) {
                continue;
            }
            out.add(make(title, place, teacher, rawDesc, t, t + duration, allDay, kind));
        }
    }

    private static Event make(String title, String place, String teacher, String rawDesc,
                              long start, long end, boolean allDay, String kind) {
        Event e = new Event();
        if (!kind.isEmpty()) {
            e.kind = normalizeKind(kind);
        }
        e.title = title;
        e.place = place == null ? "" : place.trim();
        e.teacher = teacher == null ? "" : teacher.trim();
        e.rawDesc = rawDesc == null ? "" : rawDesc;
        e.start = start;
        e.end = end;
        e.allDay = allDay;
        return e;
    }

    private static final String[] PLACE_LABELS = {
            "上课地点", "上课教室", "上课场地", "教室", "地点", "场地"
    };

    private static String extractPlace(String desc) {
        if (desc == null || desc.isEmpty()) {
            return "";
        }
        for (String line : desc.split("\n")) {
            String s = line.trim();
            for (String label : PLACE_LABELS) {
                if (s.startsWith(label + "：") || s.startsWith(label + ":")) {
                    String v = s.substring(label.length() + 1).trim();
                    if (!v.isEmpty()) {
                        return v;
                    }
                }
            }
        }
        return "";
    }

    private static final String[] TEACHER_LABELS = {
            "任课教师", "授课教师", "主讲教师", "上课教师", "教师", "老师", "主讲"
    };

    /** CATEGORIES 归一化成三种标签（导出方偶尔会带逗号分隔的多个分类，取第一个认识的） */
    private static String normalizeKind(String raw) {
        String v = raw.toUpperCase(Locale.US);
        if (v.contains("考试") || v.contains("EXAM")) {
            return "考试";
        }
        if (v.contains("日程") || v.contains("SCHEDULE") || v.contains("MEETING")) {
            return "日程";
        }
        return "课程";
    }

    /**
     * 从 DESCRIPTION 里找教师。各校各 App 导出的 ics 前缀五花八门
     * （「教师：」「任课教师：」「老师：」…），所以按标签逐个试。
     * 再兜底 ORGANIZER;CN= / ATTENDEE;CN=（部分课程表 App 把教师写在这）。
     */
    private static String extractTeacher(String desc, String organizer, String attendee) {
        String fromDesc = teacherFromText(desc);
        if (!fromDesc.isEmpty()) {
            return fromDesc;
        }
        String cn = cnOf(organizer);
        if (!cn.isEmpty()) {
            return cn;
        }
        return cnOf(attendee);
    }

    private static String teacherFromText(String desc) {
        if (desc == null || desc.isEmpty()) {
            return "";
        }
        for (String line : desc.split("\n")) {
            String s = line.trim();
            for (String label : TEACHER_LABELS) {
                if (s.startsWith(label + "：") || s.startsWith(label + ":")) {
                    String v = s.substring(label.length() + 1).trim();
                    if (!v.isEmpty()) {
                        return v;
                    }
                } else if (s.startsWith(label) && s.length() > label.length()
                        && (s.charAt(label.length()) == ' ' || s.charAt(label.length()) == '\t')) {
                    // 「教师 张三」这种没冒号的写法
                    String v = s.substring(label.length()).trim();
                    if (!v.isEmpty()) {
                        return v;
                    }
                }
            }
        }
        return "";
    }

    /** ORGANIZER;CN=张三:mailto:x@y → 张三 */
    private static String cnOf(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String[] parts = raw.split(";");
        for (String p : parts) {
            String t = p.trim();
            if (t.regionMatches(true, 0, "CN=", 0, 3)) {
                String v = t.substring(3).trim();
                if (v.startsWith("\"") && v.endsWith("\"") && v.length() > 1) {
                    v = v.substring(1, v.length() - 1);
                }
                int colon = v.indexOf(':');
                if (colon > 0) {
                    v = v.substring(0, colon);
                }
                return v.trim();
            }
        }
        return "";
    }

    /* ---------------- 时间解析 ---------------- */

    private static long parseDateMillis(String value, String line) {
        if (value == null) {
            return 0;
        }
        String v = value.trim();
        if (v.isEmpty()) {
            return 0;
        }
        boolean utc = v.endsWith("Z") || v.endsWith("z");
        if (utc) {
            v = v.substring(0, v.length() - 1);
        }
        String tzid = paramOf(line, "TZID");
        TimeZone tz = TimeZone.getDefault();
        if (utc) {
            tz = TimeZone.getTimeZone("UTC");
        } else if (tzid != null && !tzid.isEmpty()) {
            tz = TimeZone.getTimeZone(tzid);
        }
        try {
            Calendar c = Calendar.getInstance(tz, Locale.US);
            c.clear();
            if (v.length() >= 15) {
                c.set(Integer.parseInt(v.substring(0, 4)), atoi2(v, 4) - 1, atoi2(v, 6),
                        atoi2(v, 9), atoi2(v, 11), atoi2(v, 13));
            } else if (v.length() >= 8) {
                c.set(Integer.parseInt(v.substring(0, 4)), atoi2(v, 4) - 1, atoi2(v, 6), 0, 0, 0);
            } else {
                return 0;
            }
            return c.getTimeInMillis();
        } catch (Exception e) {
            return 0;
        }
    }

    private static int atoi2(String s, int pos) {
        return Integer.parseInt(s.substring(pos, pos + 2));
    }

    private static boolean isAllDay(String value, String line) {
        String v = value.trim();
        if ("DATE".equalsIgnoreCase(paramOf(line, "VALUE"))) {
            return true;
        }
        return v.length() == 8;
    }

    private static long parseDuration(String d) {
        long total = 0;
        String s = d.trim().toUpperCase(Locale.US);
        if (!s.startsWith("P")) {
            return 45L * 60000L;
        }
        boolean inTime = false;
        int i = 1;
        StringBuilder num = new StringBuilder();
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == 'T') {
                inTime = true;
                i++;
                continue;
            }
            if (Character.isDigit(c)) {
                num.append(c);
                i++;
                continue;
            }
            long n = num.length() == 0 ? 0 : Long.parseLong(num.toString());
            num.setLength(0);
            if (c == 'W') {
                total += n * 7L * DAY_MS;
            } else if (c == 'D') {
                total += n * DAY_MS;
            } else if (c == 'H') {
                total += n * 3600000L;
            } else if (c == 'M') {
                total += inTime ? n * 60000L : n * 30L * DAY_MS;
            } else if (c == 'S') {
                total += n * 1000L;
            }
            i++;
        }
        return total > 0 ? total : 45L * 60000L;
    }

    /* ---------------- RRULE 展开（只做 WEEKLY） ---------------- */

    private static List<Long> expandWeekly(long start, String rrule, String line) {
        List<Long> out = new ArrayList<>();
        Map<String, String> rule = new HashMap<>();
        for (String part : rrule.split(";")) {
            int eq = part.indexOf('=');
            if (eq > 0) {
                rule.put(part.substring(0, eq).trim().toUpperCase(Locale.US),
                        part.substring(eq + 1).trim());
            }
        }
        String freq = rule.get("FREQ");
        if (freq == null || !freq.toUpperCase(Locale.US).startsWith("WEEK")) {
            out.add(start);
            return out;
        }
        int interval = Math.max(1, parseInt(rule.get("INTERVAL"), 1));
        int count = parseInt(rule.get("COUNT"), -1);
        long until = 0;
        String u = rule.get("UNTIL");
        if (u != null && !u.isEmpty()) {
            until = parseDateMillis(u, line);
        }
        long horizon = start + HORIZON_MS;
        if (until > 0 && until < horizon) {
            horizon = until;
        }

        Set<Integer> days = parseByDay(rule.get("BYDAY"));
        if (days.isEmpty()) {
            long t = start;
            int n = 0;
            while (t <= horizon && (count < 0 || n < count) && out.size() < MAX_OCCURRENCES) {
                out.add(t);
                n++;
                t += interval * 7L * DAY_MS;
            }
            return out;
        }

        Calendar base = Calendar.getInstance(TimeZone.getDefault());
        base.setTimeInMillis(start);
        int dow = base.get(Calendar.DAY_OF_WEEK);
        int offsetToMonday = (dow == Calendar.SUNDAY) ? 6 : dow - 2;
        base.add(Calendar.DAY_OF_MONTH, -offsetToMonday);
        base.set(Calendar.HOUR_OF_DAY, 0);
        base.set(Calendar.MINUTE, 0);
        base.set(Calendar.SECOND, 0);
        base.set(Calendar.MILLISECOND, 0);
        long monday = base.getTimeInMillis();
        long timeOfDay = start - startOfDay(start);

        List<Integer> sorted = new ArrayList<>(days);
        Collections.sort(sorted);

        int n = 0;
        for (int week = 0; week < 70 && out.size() < MAX_OCCURRENCES; week++) {
            for (Integer d : sorted) {
                long t = monday + (week * (long) interval * 7L + d) * DAY_MS + timeOfDay;
                if (t < start || t > horizon) {
                    continue;
                }
                out.add(t);
                n++;
                if (count > 0 && n >= count) {
                    return out;
                }
            }
        }
        return out;
    }

    private static Set<Integer> parseByDay(String byday) {
        Set<Integer> days = new HashSet<>();
        if (byday == null || byday.isEmpty()) {
            return days;
        }
        for (String raw : byday.split(",")) {
            String s = raw.trim().toUpperCase(Locale.US);
            if (s.length() < 2) {
                continue;
            }
            String two = s.substring(Math.max(0, s.length() - 2));
            if ("MO".equals(two)) {
                days.add(0);
            } else if ("TU".equals(two)) {
                days.add(1);
            } else if ("WE".equals(two)) {
                days.add(2);
            } else if ("TH".equals(two)) {
                days.add(3);
            } else if ("FR".equals(two)) {
                days.add(4);
            } else if ("SA".equals(two)) {
                days.add(5);
            } else if ("SU".equals(two)) {
                days.add(6);
            }
        }
        return days;
    }

    private static int parseInt(String s, int def) {
        if (s == null) {
            return def;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private static long startOfDay(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }
}
