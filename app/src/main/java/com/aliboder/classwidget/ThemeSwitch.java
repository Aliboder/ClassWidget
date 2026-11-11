package com.aliboder.classwidget;

import android.app.Activity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * 主题切换的过渡（2026-11-11 用户要求「渐变而不是突变」）。
 *
 * 做法：切换那一刻标记一次 → recreate → 新界面 onCreate 里让整窗淡入。
 *
 * 为什么不用 createCircularReveal：recreate 会重建窗口，新 DecorView 的
 * attach 时序和动画起点对不上，实测录不到展开过程（要么直接跳变，要么圆外露黑）。
 * 淡入是同一类视觉目标里最稳的实现，也不会因为窗口重建而失效。
 */
public final class ThemeSwitch {

    private static boolean pending = false;

    private ThemeSwitch() {
    }

    /** 切换主题前调用 */
    public static void mark() {
        pending = true;
    }

    /** 新 Activity 的 onCreate（setContentView 之后）调用 */
    public static void play(final Activity a) {
        if (!pending) {
            return;
        }
        pending = false;
        final View decor = a.getWindow().getDecorView();
        // 等一帧再量：onCreate 里视图还没 attach，尺寸可能是 0
        decor.post(new Runnable() {
            @Override
            public void run() {
                decor.setAlpha(0f);
                decor.animate()
                        .alpha(1f)
                        .setDuration(280)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();
            }
        });
    }
}
