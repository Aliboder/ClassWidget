package com.aliboder.classwidget;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.util.TypedValue;

/**
 * 主题模式（2026-11-10）：
 *   · 跟随系统（默认）
 *   · 浅色
 *   · 深色
 *
 * App 界面靠切换 Theme（布局里写 ?attr/colorXxx）；
 * 桌面组件由 ScheduleWidgetProvider 运行时覆盖颜色（RemoteViews 拿不到主题属性）。
 */
public final class Theme {

    public static final int AUTO = 0;
    public static final int LIGHT = 1;
    public static final int DARK = 2;

    private Theme() {
    }

    public static int mode(Context c) {
        int m = WidgetPrefs.sp(c).getInt("theme_mode", AUTO);
        return (m >= 0 && m <= 2) ? m : AUTO;
    }

    public static void setMode(Context c, int m) {
        WidgetPrefs.sp(c).edit().putInt("theme_mode", m).apply();
    }

    /** 当前是否深色（跟随系统时读配置） */
    public static boolean isDark(Context c) {
        int m = mode(c);
        if (m == LIGHT) {
            return false;
        }
        if (m == DARK) {
            return true;
        }
        int ui = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return ui == Configuration.UI_MODE_NIGHT_YES;
    }

    /** 在 super.onCreate 之前调用，给 Activity 套上对应主题 */
    public static void apply(Activity a) {
        a.setTheme(isDark(a) ? R.style.AppTheme_Dark : R.style.AppTheme_Light);
    }

    /** 取主题里定义的颜色（代码里画 View 时用） */
    public static int color(Context c, int attr) {
        TypedValue tv = new TypedValue();
        if (c.getTheme().resolveAttribute(attr, tv, true)) {
            return tv.data;
        }
        return 0xFF000000;
    }

    // 代码里画 View 用的取色：直接按明暗映射。
    // ⛔ 不走 resolveAttribute —— 实测在 setTheme 之后取到的值不可靠，
    //    界面会画出「深色文字配浅色底」这种看不清的组合。
    // 布局 XML 里仍然用 ?attr（那条链路是可靠的）。
    public static int pageBg(Context c) { return isDark(c) ? 0xFF0A0B0D : 0xFFF5F6F8; }
    public static int surface(Context c) { return isDark(c) ? 0xFF14171B : 0xFFFFFFFF; }
    public static int surfaceHigh(Context c) { return isDark(c) ? 0xFF1B1F24 : 0xFFF0F2F5; }
    public static int textPrimary(Context c) { return isDark(c) ? 0xFFF2F5F8 : 0xFF14171B; }
    public static int textSecondary(Context c) { return isDark(c) ? 0xFFA8B0BC : 0xFF4A525C; }
    public static int textTertiary(Context c) { return isDark(c) ? 0xFF6B7480 : 0xFF8A929C; }
    public static int stroke(Context c) { return isDark(c) ? 0x1FFFFFFF : 0x14000000; }
    public static int sheet(Context c) { return isDark(c) ? 0xFF16191D : 0xFFFFFFFF; }
}
