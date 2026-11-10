package com.aliboder.classwidget;

/**
 * 课程色 —— 与小程序 miniprogram-v2/theme/palette.js 完全同口径。
 *
 * 课名 → 规范化 → hash(131 乘法累加) → murmur3 finalizer 混合 → % 18 → 取色。
 * 同一门课在小程序和组件里颜色一致。
 */
public final class CourseColors {

    private static final int[] PILL_RES = {
            R.drawable.pill_c0, R.drawable.pill_c1, R.drawable.pill_c2, R.drawable.pill_c3,
            R.drawable.pill_c4, R.drawable.pill_c5, R.drawable.pill_c6, R.drawable.pill_c7,
            R.drawable.pill_c8, R.drawable.pill_c9, R.drawable.pill_c10, R.drawable.pill_c11,
            R.drawable.pill_c12, R.drawable.pill_c13, R.drawable.pill_c14, R.drawable.pill_c15,
            R.drawable.pill_c16, R.drawable.pill_c17
    };

    private static final int[] COLORS = {
            0xFF96003A, 0xFFC00021, 0xFFD62200, 0xFF990000, 0xFFAE3600, 0xFFB15E00, 0xFF696A00, 0xFF006200, 0xFF007B3F, 0xFF005583, 0xFF0065C0, 0xFF2467F2, 0xFF3C27C0, 0xFF6D37D2, 0xFF9840D5, 0xFF7F0090, 0xFFA9008E, 0xFFCB1981
    };

    private CourseColors() {
    }

    /** 课名 → 胶囊/色条的 drawable 资源 */
    public static int res(String name) {
        return PILL_RES[indexOf(name)];
    }

    /** 课程色（ARGB），用于需要自己画背景/半透明的地方 */
    public static int color(String name) {
        return COLORS[indexOf(name)];
    }

    public static int indexOf(String name) {
        int h = hashOfName(name);
        int m = h % PILL_RES.length;
        return m < 0 ? m + PILL_RES.length : m;
    }

    static String normalize(String name) {
        String s = name == null ? "" : name;
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\u200B' || c == '\u200C' || c == '\u200D' || c == '\uFEFF' || c == '\u00A0') {
                continue;
            }
            if (c == '\uFF08') {
                b.append('(');
            } else if (c == '\uFF09') {
                b.append(')');
            } else if (c == '\uFF1A') {
                b.append(':');
            } else if (c == '\uFF0C') {
                b.append(',');
            } else if (c == '\uFF1B') {
                b.append(';');
            } else if (c == '\u3001') {
                b.append(',');
            } else if (c >= '\uFF01' && c <= '\uFF5E') {
                b.append((char) (c - 0xFEE0));
            } else if (Character.isWhitespace(c)) {
                continue;
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }

    static int hashOfName(String name) {
        String s = normalize(name);
        int h = 0;
        for (int i = 0; i < s.length(); i++) {
            h = h * 131 + s.charAt(i);
        }
        h ^= h >>> 16;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        h *= 0xc2b2ae35;
        h ^= h >>> 16;
        return h;
    }
}
