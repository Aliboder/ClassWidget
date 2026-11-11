package com.aliboder.classwidget;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.util.TypedValue;

/**
 * 深色 / 浅色（2026-11-11 重做：改用 Android 原生资源机制）。
 *
 * 旧做法是「自定义 attr + 两套 Theme + 代码里 if(isDark) 分叉」——
 * 每画一个 View 都要判断一次，漏一处就是「深色底配深色字」。
 *
 * 新做法（官方标准、无第三方依赖）：
 *   · 颜色只定义一次：values/colors.xml（浅色）+ values-night/colors.xml（深色），同名覆盖
 *   · 手动切换时在 attachBaseContext 里覆盖 uiMode，系统就会选到对应的那套资源
 *   · 布局写 @color/xxx，代码写 getColor(R.color.xxx) —— 全部自动跟随，没有分叉可漏
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

    /**
     * 给 Activity 套上「用户选的」配置。
     * 在 attachBaseContext 里调用，之后该 Activity 拿到的资源就是对应的那套。
     */
    public static Context wrap(Context base) {
        Configuration cfg = new Configuration(base.getResources().getConfiguration());
        int m = mode(base);
        int night;
        if (m == DARK) {
            night = Configuration.UI_MODE_NIGHT_YES;
        } else if (m == LIGHT) {
            night = Configuration.UI_MODE_NIGHT_NO;
        } else {
            night = cfg.uiMode & Configuration.UI_MODE_NIGHT_MASK;   // 跟随系统
        }
        cfg.uiMode = (cfg.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | night;
        return base.createConfigurationContext(cfg);
    }

    /** 当前生效的是不是深色（读的是已经覆盖过的配置） */
    public static boolean isDark(Context c) {
        int ui = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return ui == Configuration.UI_MODE_NIGHT_YES;
    }

    /** 取色：直接读资源，浅色/深色自动对 */
    public static int color(Context c, int resId) {
        return c.getColor(resId);
    }
}
