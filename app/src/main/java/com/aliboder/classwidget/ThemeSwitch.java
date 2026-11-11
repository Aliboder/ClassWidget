package com.aliboder.classwidget;

import android.app.Activity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * 主题切换的过渡（2026-11-11）：切换前标记，recreate 后整窗 280ms 淡入。
 *
 * 为什么不用 createCircularReveal：recreate 重建窗口后，新 DecorView 的 attach
 * 时序和动画起点对不上，实测录不到展开过程，改用淡入（同类目标里最稳的实现）。
 */
public final class ThemeSwitch {

    private static boolean pending = false;

    private ThemeSwitch() {
    }

    public static void mark() {
        pending = true;
    }

    public static void play(final Activity a) {
        if (!pending) {
            return;
        }
        pending = false;
        final View decor = a.getWindow().getDecorView();
        decor.post(new Runnable() {
            @Override
            public void run() {
                decor.setAlpha(0f);
                decor.animate().alpha(1f).setDuration(280)
                        .setInterpolator(new DecelerateInterpolator()).start();
            }
        });
    }
}
