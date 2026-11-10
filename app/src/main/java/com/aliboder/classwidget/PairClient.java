package com.aliboder.classwidget;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * 配对码拉取：小程序把课表传到云函数换 6 位码，这里凭码取回 ics 全文。
 *
 * 只有 GET 一个动作；服务端那侧是「取走即删 + 5 分钟过期」，
 * 所以同一个码第二次一定失败（404/410），失败信息直接给用户看。
 *
 * ⛔ 全部同步调用，必须在后台线程跑。
 */
public class PairClient {

    /**
     * 云开发 HTTP 网关，按顺序尝试。
     *
     * 为什么两个：小程序有两套环境 —— 正式版（release）连 stable，体验版/开发版连 cloud1。
     * 只连 stable 的话，拿体验版生成的码会一直 404，没法自测。
     * 顺序上 stable 优先（正式用户一次命中），cloud1 只在「码不存在」时才兜底，
     * 过期 / 格式错这类确定性结果不重试。
     */
    static final String[] ENDPOINTS = {
            "https://stable-d4gw6sm881fb66040-1477658038.ap-shanghai.app.tcloudbase.com/pair",
            "https://cloud1-d4g7hqf71dc70e821-1477658038.ap-shanghai.app.tcloudbase.com/pair"
    };

    static class Result {
        boolean ok;
        String ics;
        String name;
        String reason;    // 机器可读原因（NOT_FOUND / EXPIRED / BAD_CODE / ...）
        String message;   // 失败原因（已翻译成中文）
    }

    static Result fetch(String code) {
        Result last = null;
        for (String endpoint : ENDPOINTS) {
            Result r = fetchFrom(endpoint, code);
            if (r.ok) {
                return r;
            }
            last = r;
            // 只有「码不存在」才值得换下一个环境再试
            if (!"NOT_FOUND".equals(r.reason)) {
                return r;
            }
        }
        return last == null ? new Result() : last;
    }

    private static Result fetchFrom(String endpoint, String code) {
        Result r = new Result();
        HttpURLConnection conn = null;
        try {
            URL url = new URL(endpoint + "?code=" + code);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(12000);
            conn.setReadTimeout(20000);
            conn.setRequestProperty("Accept", "application/json");

            int status = conn.getResponseCode();
            InputStream in = status >= 200 && status < 400
                    ? conn.getInputStream()
                    : conn.getErrorStream();
            String body = readAll(in);

            if (body.isEmpty()) {
                r.reason = status == 404 ? "NOT_FOUND" : "HTTP_" + status;
                r.message = status == 404 ? "配对码不存在或已被使用" : "服务器无响应（" + status + "）";
                return r;
            }

            JSONObject json = new JSONObject(body);
            if (json.optBoolean("ok", false)) {
                r.ok = true;
                r.ics = json.optString("ics", "");
                r.name = json.optString("name", "课表.ics");
                if (r.ics.isEmpty()) {
                    r.ok = false;
                    r.message = "服务器返回的课表是空的";
                }
                return r;
            }

            String reason = json.optString("reason", "");
            r.reason = reason;
            String msg = json.optString("message", "");
            if (!msg.isEmpty()) {
                r.message = msg;
            } else if ("EXPIRED".equals(reason)) {
                r.message = "配对码已过期，请在小程序里重新生成";
            } else if ("NOT_FOUND".equals(reason)) {
                r.message = "配对码不存在或已被使用";
            } else if ("BAD_CODE".equals(reason)) {
                r.message = "配对码应为 6 位数字";
            } else {
                r.message = "获取失败（" + status + "）";
            }
            return r;
        } catch (Exception e) {
            r.reason = "NETWORK";
            r.message = "网络异常：" + shortMsg(e);
            return r;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static String readAll(InputStream in) throws Exception {
        if (in == null) {
            return "";
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        in.close();
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String shortMsg(Exception e) {
        String s = e.getMessage();
        if (s == null || s.isEmpty()) {
            s = e.getClass().getSimpleName();
        }
        return s.length() > 60 ? s.substring(0, 60) : s;
    }
}
