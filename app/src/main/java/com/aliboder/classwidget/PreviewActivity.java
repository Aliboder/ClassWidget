package com.aliboder.classwidget;

import android.app.Activity;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.RemoteViews;

import java.util.ArrayList;
import java.util.List;
import java.util.Calendar;

/**
 * 样式编辑：改透明度 / 字号 / 地点 / 教师，边拖边看，保存立刻刷桌面组件。
 * 预览用 RemoteViews.apply()，和桌面同一条渲染链路。
 */
public class PreviewActivity extends Activity {

    private static final int MIN_W = 140;
    private static final int MIN_H = 80;
    private static final int MIN_ALPHA = 0;
    private static final int MIN_FONT = 70;
    private static final int CARD_H_BASE = 40;
    private static final int CARD_H_MAX = 51;

    /** 0 = 自动；n > 0 → 40 + n - 1 */
    private int cardHeightOf(int progress) {
        return progress <= 0 ? 0 : CARD_H_BASE + progress - 1;
    }

    private FrameLayout host;
    private SeekBar seekW;
    private SeekBar seekH;
    private SeekBar seekAlpha;
    private SeekBar seekFont;
    private SeekBar seekRadius;
    private SeekBar seekRadiusRoot;
    private SeekBar seekAlphaCard;
    private SeekBar seekCardH;
    private TextView txtW;
    private TextView txtH;
    private TextView txtAlpha;
    private TextView txtFont;
    private TextView txtRadius;
    private TextView txtRadiusRoot;
    private TextView txtAlphaCard;
    private TextView txtCardH;
    private TextView info;
    private CheckBox chkSample;
    private CheckBox chkPlace;
    private CheckBox chkTeacher;
    private CheckBox chkUpcoming;
    private CheckBox chkClick;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preview);

        host = findViewById(R.id.preview_host);
        seekW = findViewById(R.id.seek_w);
        seekH = findViewById(R.id.seek_h);
        seekAlpha = findViewById(R.id.seek_alpha);
        seekFont = findViewById(R.id.seek_font);
        seekRadius = findViewById(R.id.seek_radius);
        seekRadiusRoot = findViewById(R.id.seek_radius_root);
        seekAlphaCard = findViewById(R.id.seek_alpha_card);
        seekCardH = findViewById(R.id.seek_card_h);
        txtW = findViewById(R.id.txt_w);
        txtH = findViewById(R.id.txt_h);
        txtAlpha = findViewById(R.id.txt_alpha);
        txtFont = findViewById(R.id.txt_font);
        txtRadius = findViewById(R.id.txt_radius);
        txtRadiusRoot = findViewById(R.id.txt_radius_root);
        txtAlphaCard = findViewById(R.id.txt_alpha_card);
        txtCardH = findViewById(R.id.txt_card_h);
        info = findViewById(R.id.preview_info);
        chkSample = findViewById(R.id.chk_sample);
        chkPlace = findViewById(R.id.chk_place);
        chkTeacher = findViewById(R.id.chk_teacher);
        chkUpcoming = findViewById(R.id.chk_upcoming);
        chkClick = findViewById(R.id.chk_click);

        // 回显已保存的样式
        seekAlpha.setProgress(WidgetPrefs.alpha(this) - MIN_ALPHA);
        seekAlphaCard.setProgress(WidgetPrefs.cardAlpha(this));
        seekFont.setProgress(WidgetPrefs.fontPercent(this) - MIN_FONT);
        seekRadius.setMax(WidgetBgs.maxRadius());
        seekRadius.setProgress(WidgetPrefs.radius(this));
        seekRadiusRoot.setMax(WidgetBgs.maxRadius());
        seekRadiusRoot.setProgress(WidgetPrefs.rootRadius(this));
        seekCardH.setMax(CARD_H_MAX);
        int ch = WidgetPrefs.cardHeight(this);
        seekCardH.setProgress(ch <= 0 ? 0 : ch - CARD_H_BASE + 1);
        chkPlace.setChecked(WidgetPrefs.showPlace(this));
        chkTeacher.setChecked(WidgetPrefs.showTeacher(this));
        chkUpcoming.setChecked(WidgetPrefs.showUpcoming(this));
        chkClick.setChecked(WidgetPrefs.clickEnabled(this));

        SeekBar.OnSeekBarChangeListener sizeListener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                render();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        };
        seekW.setOnSeekBarChangeListener(sizeListener);
        seekH.setOnSeekBarChangeListener(sizeListener);

        SeekBar.OnSeekBarChangeListener styleListener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && seekBar != seekW && seekBar != seekH) {
                    showPresetSelection(-1);
                }
                saveAndRender();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        };
        seekAlpha.setOnSeekBarChangeListener(styleListener);
        seekAlphaCard.setOnSeekBarChangeListener(styleListener);
        seekCardH.setOnSeekBarChangeListener(styleListener);
        seekFont.setOnSeekBarChangeListener(styleListener);
        seekRadius.setOnSeekBarChangeListener(styleListener);
        seekRadiusRoot.setOnSeekBarChangeListener(styleListener);

        CompoundButton.OnCheckedChangeListener checkListener = new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                saveAndRender();
            }
        };
        chkPlace.setOnCheckedChangeListener(checkListener);
        chkTeacher.setOnCheckedChangeListener(checkListener);
        chkUpcoming.setOnCheckedChangeListener(checkListener);
        chkClick.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                WidgetPrefs.setClickEnabled(PreviewActivity.this, isChecked);
                ScheduleWidgetProvider.refreshAll(PreviewActivity.this);
            }
        });
        chkSample.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                render();
            }
        });

        presetStyle(0, 93, 90, 100, 16, 24, 0);
        presetStyle(1, 70, 75, 100, 20, 28, 0);
        presetStyle(2, 0, 100, 100, 8, 16, 0);
        presetStyle(3, 100, 100, 110, 12, 20, 0);

        findViewById(R.id.btn_reset).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPresetSelection(0);
                applyStyle(93, 90, 100, 16, 24, 0);
                Toast.makeText(PreviewActivity.this, "已恢复默认", Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.btn_refresh_smart).setOnClickListener(refreshModeListener(WidgetPrefs.REFRESH_SMART));
        findViewById(R.id.btn_refresh_saver).setOnClickListener(refreshModeListener(WidgetPrefs.REFRESH_SAVER));
        findViewById(R.id.btn_refresh_frequent).setOnClickListener(refreshModeListener(WidgetPrefs.REFRESH_FREQUENT));

        preset(R.id.btn_2x2, 158, 158);
        preset(R.id.btn_4x2, 340, 158);
        preset(R.id.btn_4x4, 340, 340);
        preset(R.id.btn_tablet, 520, 280);

        showPresetSelection(-1);
        showRefreshMode();
        render();
    }

    private void preset(int id, final int w, final int h) {
        findViewById(id).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                seekW.setProgress(w - MIN_W);
                seekH.setProgress(h - MIN_H);
                render();
            }
        });
    }

    private static final int[] PRESET_IDS = {
            R.id.btn_preset_default, R.id.btn_preset_glass,
            R.id.btn_preset_min, R.id.btn_preset_contrast
    };

    /** 当前选中的预设（-1 = 自定义） */
    private int activePreset = 0;

    private void presetStyle(final int index, int alpha, final int cardAlpha, final int font,
                             final int radius, final int rootRadius, final int cardH) {
        final int id = PRESET_IDS[index];
        findViewById(id).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPresetSelection(index);
                applyStyle(alpha, cardAlpha, font, radius, rootRadius, cardH);
            }
        });
    }

    private View.OnClickListener refreshModeListener(final int mode) {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                WidgetPrefs.setRefreshMode(PreviewActivity.this, mode);
                showRefreshMode();
                ScheduleWidgetProvider.refreshAll(PreviewActivity.this);
            }
        };
    }

    private void showRefreshMode() {
        int mode = WidgetPrefs.refreshMode(this);
        int[] ids = {R.id.btn_refresh_smart, R.id.btn_refresh_saver, R.id.btn_refresh_frequent};
        for (int i = 0; i < ids.length; i++) {
            TextView tv = findViewById(ids[i]);
            boolean on = (i == mode);
            tv.setBackgroundResource(on ? R.drawable.bg_chip_on : R.drawable.bg_chip);
            tv.setTextColor(on ? 0xFF3FD3AC : 0xFFB8BFCB);
        }
        TextView hint = findViewById(R.id.txt_refresh_hint);
        String text;
        if (mode == WidgetPrefs.REFRESH_SMART) {
            text = "上下课、零点刷新；有课进行中时每分钟刷新，倒计时和进度条会动。没课时不耗电。";
        } else if (mode == WidgetPrefs.REFRESH_SAVER) {
            text = "只在上下课和零点刷新，最省电。课中倒计时和进度条不会动。";
        } else {
            text = "每分钟刷新一次，最实时，耗电也最多。";
        }
        hint.setText(text + "\n小米/澎湃OS 需给本 App 开「自启动」和「无限制后台」才能稳定定时。");
    }

    private void showPresetSelection(int index) {
        activePreset = index;
        for (int i = 0; i < PRESET_IDS.length; i++) {
            TextView tv = findViewById(PRESET_IDS[i]);
            boolean on = (i == index);
            tv.setBackgroundResource(on ? R.drawable.bg_chip_on : R.drawable.bg_chip);
            tv.setTextColor(on ? 0xFF3FD3AC : 0xFFB8BFCB);
        }
    }

    private void applyStyle(int alpha, int cardAlpha, int font, int radius, int rootRadius, int cardH) {
        seekAlpha.setProgress(alpha);
        seekAlphaCard.setProgress(cardAlpha);
        seekFont.setProgress(font - MIN_FONT);
        seekRadius.setProgress(radius);
        seekRadiusRoot.setProgress(rootRadius);
        seekCardH.setProgress(cardH <= 0 ? 0 : cardH - 40 + 1);
        showPresetSelection(activePreset);
        saveAndRender();
    }

    private void saveAndRender() {
        WidgetPrefs.save(this,
                MIN_ALPHA + seekAlpha.getProgress(),
                seekAlphaCard.getProgress(),
                MIN_FONT + seekFont.getProgress(),
                seekRadius.getProgress(),
                seekRadiusRoot.getProgress(),
                cardHeightOf(seekCardH.getProgress()),
                chkUpcoming.isChecked(),
                chkPlace.isChecked(),
                chkTeacher.isChecked());
        ScheduleWidgetProvider.refreshAll(this);
        render();
    }

    private void render() {
        int wDp = seekW.getProgress() + MIN_W;
        int hDp = seekH.getProgress() + MIN_H;
        txtW.setText(wDp + " dp");
        txtH.setText(hDp + " dp");
        txtAlpha.setText((MIN_ALPHA + seekAlpha.getProgress()) + "%");
        txtAlphaCard.setText(seekAlphaCard.getProgress() + "%");
        int cardH = cardHeightOf(seekCardH.getProgress());
        txtCardH.setText(cardH == 0 ? "自动" : cardH + " dp");
        txtFont.setText((MIN_FONT + seekFont.getProgress()) + "%");
        txtRadius.setText(seekRadius.getProgress() + " dp");
        txtRadiusRoot.setText(seekRadiusRoot.getProgress() + " dp");

        List<Event> events = chkSample.isChecked() ? sampleEvents() : EventStore.load(this);
        float scale = ScheduleWidgetProvider.scaleFor(hDp) * (WidgetPrefs.fontPercent(this) / 100f);
        int rows = ScheduleWidgetProvider.rowsFor(hDp);
        String measured = WidgetPrefs.lastSize(this);
        info.setText("字号 " + (Math.round(10 * scale) / 10f) + "× · 显示 " + rows + " 行 · "
                + (events.isEmpty() ? "当前没有数据" : "读到 " + events.size() + " 条日程")
                + (measured.isEmpty() ? "" : "\n桌面实测： " + measured + "（预览只是模拟）"));

        RemoteViews rv = ScheduleWidgetProvider.build(this, hDp, events);
        View view;
        try {
            view = rv.apply(this, host);
        } catch (RuntimeException e) {
            info.setText("渲染失败：" + e);
            return;
        }

        // 预览区放不下时等比缩小（桌面上的真实组件不受影响）
        int wPx = dp(wDp);
        int hPx = dp(hDp);
        int availW = host.getWidth();
        if (availW <= 0) {
            availW = getResources().getDisplayMetrics().widthPixels - dp(56);
        }
        float fit = Math.min(1f, availW / (float) wPx);

        host.removeAllViews();
        FrameLayout holder = new FrameLayout(this);
        holder.setLayoutParams(new FrameLayout.LayoutParams((int) (wPx * fit), (int) (hPx * fit)));
        view.setPivotX(0f);
        view.setPivotY(0f);
        view.setScaleX(fit);
        view.setScaleY(fit);
        holder.addView(view, new FrameLayout.LayoutParams(wPx, hPx));
        host.addView(holder);
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }

    private List<Event> sampleEvents() {
        long now = System.currentTimeMillis();
        long min = 60L * 1000L;
        long hour = 60L * min;
        long day = 24L * hour;

        List<Event> list = new ArrayList<>();
        list.add(ev("运输组织学", "教1-424", now - 30 * min, now + 45 * min, "王小霞"));
        list.add(ev("交通运输法规", "教1-408", now + 2 * hour, now + 3 * hour, "邹超[主讲]"));
        list.add(ev("马克思主义基本原理", "教1-205", now + day + 2 * hour, now + day + 3 * hour, "邓少华"));
        return list;
    }

    private Event ev(String title, String place, long start, long end, String teacher) {
        Event e = new Event();
        e.title = title;
        e.place = place;
        e.teacher = teacher;
        e.start = start;
        e.end = end;
        return e;
    }
}
