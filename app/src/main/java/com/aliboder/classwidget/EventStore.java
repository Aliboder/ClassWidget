package com.aliboder.classwidget;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/** 日程落盘：filesDir/events.json（无数据库、无依赖）。 */
public final class EventStore {

    private static final String FILE = "events.json";

    private EventStore() {
    }

    public static void save(Context context, List<Event> events, String source) {
        try {
            JSONArray arr = new JSONArray();
            for (Event e : events) {
                JSONObject o = new JSONObject();
                o.put("t", e.title);
                o.put("p", e.place);
                o.put("te", e.teacher);
                o.put("d", e.rawDesc);
                o.put("s", e.start);
                o.put("e", e.end);
                o.put("a", e.allDay ? 1 : 0);
                arr.put(o);
            }
            JSONObject root = new JSONObject();
            root.put("source", source == null ? "" : source);
            root.put("importedAt", System.currentTimeMillis());
            root.put("events", arr);
            FileOutputStream out = new FileOutputStream(new File(context.getFilesDir(), FILE));
            out.write(root.toString().getBytes(Charset.forName("UTF-8")));
            out.close();
        } catch (Exception ignored) {
        }
    }

    public static List<Event> load(Context context) {
        List<Event> list = new ArrayList<>();
        File f = new File(context.getFilesDir(), FILE);
        if (!f.exists()) {
            return list;
        }
        try {
            FileInputStream in = new FileInputStream(f);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            in.close();
            JSONObject root = new JSONObject(new String(bos.toByteArray(), Charset.forName("UTF-8")));
            JSONArray arr = root.optJSONArray("events");
            if (arr == null) {
                return list;
            }
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) {
                    continue;
                }
                Event e = new Event();
                e.title = o.optString("t", "");
                e.place = o.optString("p", "");
                e.teacher = o.optString("te", "");
                e.rawDesc = o.optString("d", "");
                e.start = o.optLong("s", 0);
                e.end = o.optLong("e", 0);
                e.allDay = o.optInt("a", 0) == 1;
                if (e.start > 0) {
                    list.add(e);
                }
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    public static String source(Context context) {
        return readString(context, "source");
    }

    public static long importedAt(Context context) {
        String s = readString(context, "importedAt");
        try {
            return Long.parseLong(s);
        } catch (Exception e) {
            return 0;
        }
    }

    private static String readString(Context context, String key) {
        File f = new File(context.getFilesDir(), FILE);
        if (!f.exists()) {
            return "";
        }
        try {
            FileInputStream in = new FileInputStream(f);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            in.close();
            JSONObject root = new JSONObject(new String(bos.toByteArray(), Charset.forName("UTF-8")));
            return root.optString(key, "");
        } catch (Exception e) {
            return "";
        }
    }
}
