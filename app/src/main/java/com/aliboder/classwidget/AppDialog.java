package com.aliboder.classwidget;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 自绘对话框（2026-11-10 用户要求）。
 *
 * 为什么不用 AlertDialog：系统弹窗会套一层 Material 灰白面板，
 * 在深色页面里像贴了张纸。这里全部自绘，和 App 同一个深色语言。
 */
public final class AppDialog {

    private AppDialog() {
    }

    public interface Action {
        void run();
    }

    /**
     * @param cancelText 传 null 表示只有一个按钮
     */
    public static void show(final Activity a, String title, String message,
                            String okText, final Action onOk,
                            String cancelText, final Action onCancel) {
        final Dialog d = new Dialog(a, R.style.Theme_PairCodeDialog);
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundResource(R.drawable.pair_sheet);
        int p = dp(a, 22);
        box.setPadding(p, p, p, dp(a, 16));

        TextView t = new TextView(a);
        t.setText(title);
        t.setTextSize(18);
        t.setTextColor(0xFFF2F5F8);
        t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        box.addView(t);

        if (message != null && !message.isEmpty()) {
            TextView m = new TextView(a);
            m.setText(message);
            m.setTextSize(13.5f);
            m.setTextColor(0xFFA8B0BC);
            m.setLineSpacing(dp(a, 4), 1f);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(a, 10);
            m.setLayoutParams(lp);
            box.addView(m);
        }

        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = dp(a, 20);
        row.setLayoutParams(rlp);

        if (cancelText != null) {
            TextView cancel = new TextView(a);
            cancel.setText(cancelText);
            cancel.setTextSize(14.5f);
            cancel.setTextColor(0xFFA8B0BC);
            cancel.setGravity(Gravity.CENTER);
            cancel.setBackgroundResource(R.drawable.bg_btn_ghost);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, dp(a, 46), 1f);
            cancel.setLayoutParams(clp);
            cancel.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    d.dismiss();
                    if (onCancel != null) {
                        onCancel.run();
                    }
                }
            });
            row.addView(cancel);
        }

        TextView ok = new TextView(a);
        ok.setText(okText);
        ok.setTextSize(14.5f);
        ok.setTextColor(0xFF04120E);
        ok.setGravity(Gravity.CENTER);
        ok.setTypeface(ok.getTypeface(), android.graphics.Typeface.BOLD);
        GradientDrawable g = new GradientDrawable();
        g.setColor(WidgetPrefs.accentColor(a));
        g.setCornerRadius(dp(a, 14));
        ok.setBackground(g);
        LinearLayout.LayoutParams okp = new LinearLayout.LayoutParams(0, dp(a, 46), 1f);
        if (cancelText != null) {
            okp.leftMargin = dp(a, 10);
        }
        ok.setLayoutParams(okp);
        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
                if (onOk != null) {
                    onOk.run();
                }
            }
        });
        row.addView(ok);

        box.addView(row);

        d.setContentView(box);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setGravity(Gravity.CENTER);
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.getDecorView().setPadding(dp(a, 28), 0, dp(a, 28), 0);
        }
        d.show();
    }

    private static int dp(Activity a, int v) {
        return Math.round(v * a.getResources().getDisplayMetrics().density);
    }
}
