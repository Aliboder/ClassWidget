package com.aliboder.classwidget;

/**
 * 圆角只负责「轮廓 + 描边」，颜色/透明度由代码 setBackgroundColor 设，
 * 配合父容器的 clipToOutline，就能做到：
 *   · 透明度无级（0~100 任意整数）
 *   · 圆角 1dp 步进（0~40）
 * 这是 RemoteViews 里唯一能绕开"预生成组合"的做法。
 */
public final class WidgetBgs {

    private static final int MAX_RADIUS = 40;

    private WidgetBgs() {
    }

    public static int round(int radiusDp) {
        int r = Math.max(0, Math.min(MAX_RADIUS, radiusDp));
        return ROUND[r];
    }

    public static int maxRadius() {
        return MAX_RADIUS;
    }

    private static final int[] ROUND = {
            R.drawable.round_r0, R.drawable.round_r1, R.drawable.round_r2, R.drawable.round_r3,
            R.drawable.round_r4, R.drawable.round_r5, R.drawable.round_r6, R.drawable.round_r7,
            R.drawable.round_r8, R.drawable.round_r9, R.drawable.round_r10, R.drawable.round_r11,
            R.drawable.round_r12, R.drawable.round_r13, R.drawable.round_r14, R.drawable.round_r15,
            R.drawable.round_r16, R.drawable.round_r17, R.drawable.round_r18, R.drawable.round_r19,
            R.drawable.round_r20, R.drawable.round_r21, R.drawable.round_r22, R.drawable.round_r23,
            R.drawable.round_r24, R.drawable.round_r25, R.drawable.round_r26, R.drawable.round_r27,
            R.drawable.round_r28, R.drawable.round_r29, R.drawable.round_r30, R.drawable.round_r31,
            R.drawable.round_r32, R.drawable.round_r33, R.drawable.round_r34, R.drawable.round_r35,
            R.drawable.round_r36, R.drawable.round_r37, R.drawable.round_r38, R.drawable.round_r39,
            R.drawable.round_r40
    };
}
