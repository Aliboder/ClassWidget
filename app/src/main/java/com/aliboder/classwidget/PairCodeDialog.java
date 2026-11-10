package com.aliboder.classwidget;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

/**
 * 配对码输入对话框（自绘外壳，但输入走系统键盘）。
 *
 * 设计取舍（2026-11-10 用户定稿）：
 *   · 外观自己做 —— 系统 AlertDialog 会套白底 Material 卡片，跟深色 App 割裂；
 *   · 输入用用户的键盘 —— 自绘数字键盘既多余又不如手机自带键盘顺手；
 *   · 打开时自动读剪贴板 —— 小程序那边点了「复制配对码」，到这里就不用再敲一遍。
 *
 * 实现：6 个格子只是「视觉层」，真正接键盘的是覆盖在上面的透明 EditText；
 *       点格子任意位置、或直接点输入层，都会唤起系统键盘。
 */
public class PairCodeDialog extends Dialog {

    public interface OnSubmit {
        void onSubmit(String code);
    }

    private final OnSubmit onSubmit;

    private final TextView[] cells = new TextView[6];
    private EditText input;
    private TextView ok;

    public PairCodeDialog(Context context, OnSubmit onSubmit) {
        super(context, R.style.Theme_PairCodeDialog);
        this.onSubmit = onSubmit;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_pair_code);

        cells[0] = findViewById(R.id.pc_cell1);
        cells[1] = findViewById(R.id.pc_cell2);
        cells[2] = findViewById(R.id.pc_cell3);
        cells[3] = findViewById(R.id.pc_cell4);
        cells[4] = findViewById(R.id.pc_cell5);
        cells[5] = findViewById(R.id.pc_cell6);
        input = findViewById(R.id.pc_input);
        ok = findViewById(R.id.pc_ok);

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                refresh();
            }
        });

        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String code = input.getText().toString().trim();
                if (code.length() == 6) {
                    dismiss();
                    onSubmit.onSubmit(code);
                }
            }
        });

        findViewById(R.id.pc_cancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });

        // 自动填入：小程序复制过来的码
        String fromClip = readClipCode();
        if (fromClip != null) {
            input.setText(fromClip);
            input.setSelection(fromClip.length());
            TextView tip = findViewById(R.id.pc_tip);
            tip.setText("已从剪贴板填入配对码，确认无误后点「获取课表」");
        }

        refresh();
    }

    @Override
    public void show() {
        super.show();
        Window w = getWindow();
        if (w != null) {
            w.setBackgroundDrawableResource(android.R.color.transparent);
            w.setGravity(Gravity.CENTER);
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            // 键盘直接弹出来，少点一次
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            w.getDecorView().setPadding(dip(20), 0, dip(20), 0);
        }
        input.requestFocus();
        input.postDelayed(new Runnable() {
            @Override
            public void run() {
                InputMethodManager imm = (InputMethodManager)
                        getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
                }
            }
        }, 120);
    }

    /** 剪贴板里正好是 6 位数字才用；其它内容一律忽略（别把用户逗号、链接填进来） */
    private String readClipCode() {
        try {
            ClipboardManager cm = (ClipboardManager)
                    getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null || !cm.hasPrimaryClip()) {
                return null;
            }
            ClipData clip = cm.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) {
                return null;
            }
            CharSequence text = clip.getItemAt(0).coerceToText(getContext());
            if (text == null) {
                return null;
            }
            // 容忍小程序复制出来的「165 247」这种带空格形态
            String digits = text.toString().replaceAll("[^0-9]", "");
            return digits.length() == 6 ? digits : null;
        } catch (Exception e) {
            return null;
        }
    }

    private int dip(int dp) {
        return Math.round(dp * getContext().getResources().getDisplayMetrics().density);
    }

    private void refresh() {
        String code = input.getText().toString();
        for (int i = 0; i < 6; i++) {
            boolean filled = i < code.length();
            cells[i].setText(filled ? String.valueOf(code.charAt(i)) : "");
            cells[i].setBackgroundResource(
                    filled ? R.drawable.pair_cell_on : R.drawable.pair_cell);
        }
        boolean ready = code.length() == 6;
        ok.setAlpha(ready ? 1f : 0.45f);
        ok.setEnabled(ready);
    }
}
