package com.aliboder.classwidget;

import android.content.Context;
import android.content.SharedPreferences;

/** 组件的样式设置（存在 SharedPreferences 里，改完立刻刷组件）。 */
public final class WidgetPrefs {

    private static final String NAME = "widget_style";

    private WidgetPrefs() {
    }

    public static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    /** 背景不透明度百分比（50 ~ 100） */
    public static int alpha(Context c) {
        return sp(c).getInt("alpha", 93);
    }

    /** 卡片不透明度百分比（0 ~ 100） */
    public static int cardAlpha(Context c) {
        return sp(c).getInt("cardAlpha", 90);
    }

    /** 字号百分比（70 ~ 150） */
    public static int fontPercent(Context c) {
        return sp(c).getInt("font", 100);
    }

    /** 卡片圆角（dp） */
    public static int radius(Context c) {
        return sp(c).getInt("radius", 16);
    }

    /** 卡片高度（dp）；0 = 自动铺满 */
    public static int cardHeight(Context c) {
        return sp(c).getInt("cardHeight", 0);
    }

    /** 组件外框圆角（dp） */
    public static int rootRadius(Context c) {
        return sp(c).getInt("rootRadius", 24);
    }

    /** 刷新模式：0=智能（课中每分钟） 1=省电（只在上下课） 2=频繁（每分钟） */
    public static final int REFRESH_SMART = 0;
    public static final int REFRESH_SAVER = 1;
    public static final int REFRESH_FREQUENT = 2;

    /** 固定「频繁」（2026-11-11 用户：不再让用户选，统一每分钟刷新） */
    public static int refreshMode(Context c) {
        return REFRESH_FREQUENT;
    }

    public static void setRefreshMode(Context c, int mode) {
        sp(c).edit().putInt("refresh_mode", mode).apply();
    }

    /** true = 点击组件打开 App，false = 组件不响应点击 */
    public static boolean clickEnabled(Context c) {
        return sp(c).getBoolean("click_enabled", true);
    }

    public static void setClickEnabled(Context c, boolean v) {
        sp(c).edit().putBoolean("click_enabled", v).apply();
    }

    /** true = 显示未来几天（滚满为止），false = 只显示今天 */
    public static boolean showUpcoming(Context c) {
        return sp(c).getBoolean("upcoming", false);
    }

    public static boolean showPlace(Context c) {
        return sp(c).getBoolean("place", true);
    }

    public static boolean showTeacher(Context c) {
        return sp(c).getBoolean("teacher", true);
    }

    /* ---------- 2026-11-10 新增：给用户更大的编辑自由度 ---------- */

    /** 强调色（时间块 / 进度条 / 高亮）。默认主题绿 */
    public static final int[] ACCENTS = {
            0xFF3FD3AC, 0xFF4D9BFF, 0xFFB07CFF, 0xFFFF7A9C, 0xFFFFA53D, 0xFF57C7E8
    };

    public static int accentIndex(Context c) {
        int i = sp(c).getInt("accent", 0);
        return (i >= 0 && i < ACCENTS.length) ? i : 0;
    }

    public static int accentColor(Context c) {
        return ACCENTS[accentIndex(c)];
    }

    /** 固定 24 小时制（2026-11-11 用户：去掉切换，默认 24h） */
    public static boolean hour24(Context c) {
        return true;
    }

    /** 是否在卡片右上角显示「课程 / 考试 / 日程」标签 */
    public static boolean showKind(Context c) {
        return sp(c).getBoolean("showKind", true);
    }

    /** 卡片间距档位：0=紧凑 1=标准 2=宽松 */
    public static int cardGap(Context c) {
        return sp(c).getInt("cardGap", 1);
    }

    /** 组件第一次渲染时把桌面给的尺寸记下来，供预览页对照 */
    public static void recordSize(Context c, int wDp, int hDp) {
        sp(c).edit().putInt("lastW", wDp).putInt("lastH", hDp)
                .putLong("lastAt", System.currentTimeMillis()).apply();
    }

    /** 例：328 × 152 dp；没记录过 → "" */
    public static String lastSize(Context c) {
        int w = sp(c).getInt("lastW", 0);
        int h = sp(c).getInt("lastH", 0);
        return w <= 0 || h <= 0 ? "" : w + " × " + h + " dp";
    }

    public static void save(Context c, int alpha, int cardAlpha, int font, int radius, int rootRadius,
                            int cardHeight, boolean upcoming, boolean place, boolean teacher) {
        sp(c).edit()
                .putInt("alpha", alpha)
                .putInt("cardAlpha", cardAlpha)
                .putInt("radius", radius)
                .putInt("rootRadius", rootRadius)
                .putInt("cardHeight", cardHeight)
                .putBoolean("upcoming", upcoming)
                .putInt("font", font)
                .putBoolean("place", place)
                .putBoolean("teacher", teacher)
                .apply();
    }
}
