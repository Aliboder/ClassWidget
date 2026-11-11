package com.aliboder.classwidget;

import android.content.Context;

import android.app.Activity;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
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

    /** 预览按 4×2 画（桌面上实际尺寸由格子决定，这里只求看得清效果） */
    private static final int PREVIEW_W_DP = 340;
    private static final int PREVIEW_H_DP = 158;

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
    private TextView info;

    /** 一行滑杆：标签 + 滑杆 + 数值，绑定逻辑收在这里 */
    private static final class SliderRow {
        final SeekBar bar;
        final TextView label;
        final TextView value;

        SliderRow(Activity a, int rootId, String labelText) {
            View root = a.findViewById(rootId);
            bar = root.findViewById(R.id.slider);
            label = root.findViewById(R.id.slider_label);
            value = root.findViewById(R.id.slider_value);
            label.setText(labelText);
        }
    }

    /** 分段档位的实际取值（与界面上的四个格子一一对应） */
    private static final int[] ROOT_RADII = {0, 12, 20, 28};
    private static final int[] CARD_RADII = {0, 8, 14, 20};
    private static final int[] CARD_HEIGHTS = {0, 42, 48, 54};   // 0 = 自动

    private int chosenRootRadius = 20;
    private int chosenCardRadius = 14;
    private int chosenCardHeight = 0;

    private SliderRow rowAlpha;
    private SliderRow rowCardAlpha;
    private SliderRow rowFont;

    /** 切主题会 recreate，滚动位置靠这个带过去（否则每次都跳回顶部） */
    private static int pendingScrollY = -1;

    /** 有未保存的改动 */
    private boolean dirty = false;
    private TextView txtDirty;
    private TextView btnSave;

    /** 进入页面时的原始值（放弃修改时回滚） */
    private int origAlpha, origCardAlpha, origFont, origRadius, origRootRadius, origCardH;
    private int origAccent, origRefresh;
    private boolean origHour24, origShowKind, origUpcoming, origPlace, origTeacher, origClick;

    private TextView chkUpcoming;
    private TextView chkPlace;
    private TextView chkTeacher;
    private TextView chkKind;
    private TextView chkClick;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Theme.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeSwitch.play(this);
        setContentView(R.layout.activity_preview);

        findViewById(R.id.preview_stage).setBackgroundResource(
                R.drawable.preview_stage);
        host = findViewById(R.id.preview_host);
        info = findViewById(R.id.preview_info);
        txtDirty = findViewById(R.id.txt_dirty);
        btnSave = findViewById(R.id.btn_save);

        origAlpha = WidgetPrefs.alpha(this);
        origCardAlpha = WidgetPrefs.cardAlpha(this);
        origFont = WidgetPrefs.fontPercent(this);
        origRadius = WidgetPrefs.radius(this);
        origRootRadius = WidgetPrefs.rootRadius(this);
        origCardH = WidgetPrefs.cardHeight(this);
        origAccent = WidgetPrefs.accentIndex(this);
        origRefresh = WidgetPrefs.refreshMode(this);
        origHour24 = WidgetPrefs.hour24(this);
        origShowKind = WidgetPrefs.showKind(this);
        origUpcoming = WidgetPrefs.showUpcoming(this);
        origPlace = WidgetPrefs.showPlace(this);
        origTeacher = WidgetPrefs.showTeacher(this);
        origClick = WidgetPrefs.clickEnabled(this);

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
            }
        });

        rowAlpha = new SliderRow(this, R.id.row_alpha, "背景透明度");
        rowCardAlpha = new SliderRow(this, R.id.row_card_alpha, "卡片透明度");
        rowFont = new SliderRow(this, R.id.row_font, "字号");

        chosenRootRadius = nearestIndex(ROOT_RADII, WidgetPrefs.rootRadius(this));
        chosenCardRadius = nearestIndex(CARD_RADII, WidgetPrefs.radius(this));
        chosenCardHeight = nearestIndex(CARD_HEIGHTS, WidgetPrefs.cardHeight(this));

        chkUpcoming = findViewById(R.id.chk_upcoming);
        chkPlace = findViewById(R.id.chk_place);
        chkTeacher = findViewById(R.id.chk_teacher);
        chkKind = findViewById(R.id.chk_kind);
        chkClick = findViewById(R.id.chk_click);

        // 回显已保存的样式
        rowAlpha.bar.setMax(100);
        rowAlpha.bar.setProgress(WidgetPrefs.alpha(this));
        rowCardAlpha.bar.setMax(100);
        rowCardAlpha.bar.setProgress(WidgetPrefs.cardAlpha(this));
        rowFont.bar.setMax(80);
        rowFont.bar.setProgress(WidgetPrefs.fontPercent(this) - MIN_FONT);

        bindSwitch(chkPlace, WidgetPrefs.showPlace(this));
        bindSwitch(chkTeacher, WidgetPrefs.showTeacher(this));
        bindSwitch(chkUpcoming, WidgetPrefs.showUpcoming(this));
        bindSwitch(chkKind, WidgetPrefs.showKind(this));
        bindSwitch(chkClick, WidgetPrefs.clickEnabled(this));

        SeekBar.OnSeekBarChangeListener listener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

                saveAndRender();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        };
        rowAlpha.bar.setOnSeekBarChangeListener(listener);
        rowCardAlpha.bar.setOnSeekBarChangeListener(listener);
        rowFont.bar.setOnSeekBarChangeListener(listener);

        bindSegments(new int[]{R.id.r_root_0, R.id.r_root_1, R.id.r_root_2, R.id.r_root_3}, ROOT_RADII,
                new Setter() {
                    public void set(int i, int v) { chosenRootRadius = i; }
                });
        bindSegments(new int[]{R.id.r_card_0, R.id.r_card_1, R.id.r_card_2, R.id.r_card_3}, CARD_RADII,
                new Setter() {
                    public void set(int i, int v) { chosenCardRadius = i; }
                });
        bindSegments(new int[]{R.id.h_auto, R.id.h_min, R.id.h_mid, R.id.h_max}, CARD_HEIGHTS,
                new Setter() {
                    public void set(int i, int v) { chosenCardHeight = i; }
                });

        // 强调色：6 个色点
        LinearLayout accentRow = findViewById(R.id.accent_row);
        accentRow.removeAllViews();
        for (int i = 0; i < WidgetPrefs.ACCENTS.length; i++) {
            final int idx = i;
            View dot = new View(this);
            int size = dp(34);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMarginEnd(dp(10));
            dot.setLayoutParams(lp);
            android.graphics.drawable.GradientDrawable ring =
                    new android.graphics.drawable.GradientDrawable();
            ring.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            ring.setColor(WidgetPrefs.ACCENTS[i]);
            dot.setBackground(ring);
            dot.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    WidgetPrefs.sp(PreviewActivity.this).edit()
                            .putInt("accent", idx).apply();
                    refreshChoiceStyles();
                    markDirty();
                    render();
                }
            });
            accentRow.addView(dot);
        }

        // 主题：跟随系统 / 浅色 / 深色（切完要 recreate 才能换掉当前界面的主题）
        int[] themeIds = {R.id.theme_auto, R.id.theme_light, R.id.theme_dark};
        for (int i = 0; i < themeIds.length; i++) {
            final int mode = i;
            findViewById(themeIds[i]).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (Theme.mode(PreviewActivity.this) == mode) {
                        return;
                    }
                    ThemeSwitch.mark();
                    Theme.setMode(PreviewActivity.this, mode);
                    ScheduleWidgetProvider.refreshAll(PreviewActivity.this);
                    recreate();
                }
            });
        }

        findViewById(R.id.btn_reset).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                applyStyle(93, 90, 100, 16, 24, 0);
                Toast.makeText(PreviewActivity.this, "已恢复默认，记得点保存", Toast.LENGTH_SHORT).show();
            }
        });

        refreshChoiceStyles();
        render();
    }

    /** 写一个整型样式项并立刻刷新组件 */
    private void putInt(String key, int v) {
        WidgetPrefs.sp(this).edit().putInt(key, v).apply();
        refreshChoiceStyles();
        markDirty();
        render();
    }

    private void putBool(String key, boolean v) {
        WidgetPrefs.sp(this).edit().putBoolean(key, v).apply();
        refreshChoiceStyles();
        markDirty();
        render();
    }

    /** 开关行：用 drawableEnd 表示状态，点一下翻转 */
    private void bindSwitch(final TextView tv, boolean on) {
        tv.setTag(on);
        paintSwitch(tv, on);
        tv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean next = !(Boolean) tv.getTag();
                tv.setTag(next);
                paintSwitch(tv, next);
                String key;
                int id = tv.getId();
                if (id == R.id.chk_place) key = "place";
                else if (id == R.id.chk_teacher) key = "teacher";
                else if (id == R.id.chk_upcoming) key = "upcoming";
                else if (id == R.id.chk_kind) key = "showKind";
                else key = "click_enabled";
                WidgetPrefs.sp(PreviewActivity.this).edit().putBoolean(key, next).apply();
                markDirty();
                render();
            }
        });
    }

    private void paintSwitch(TextView tv, boolean on) {
        tv.setCompoundDrawablesWithIntrinsicBounds(0, 0, on ? R.drawable.ic_check_on : R.drawable.ic_check_off, 0);
        // 勾选色跟随用户选的强调色（drawable 是静态的，只能运行时 tint）
        android.graphics.drawable.Drawable[] ds = tv.getCompoundDrawables();
        if (ds[2] != null) {
            ds[2].setTint(on ? WidgetPrefs.accentColor(this) : 0x66FFFFFF);
        }
    }

    /** 胶囊选项：点击后写值并刷新选中态 */
    private void chip(final int id, final Runnable action) {
        findViewById(id).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                action.run();
            }
        });
    }

    /** 把所有「选项类」控件的选中态重画一遍（预设 / 间距 / 时间制 / 刷新 / 强调色） */
    private void refreshChoiceStyles() {
        paintChipGroup(new int[]{R.id.theme_auto, R.id.theme_light, R.id.theme_dark}, Theme.mode(this));
        paintSegments();
        // 强调色选中环
        LinearLayout row = findViewById(R.id.accent_row);
        int sel = WidgetPrefs.accentIndex(this);
        for (int i = 0; i < row.getChildCount(); i++) {
            View dot = row.getChildAt(i);
            android.graphics.drawable.GradientDrawable ring =
                    new android.graphics.drawable.GradientDrawable();
            ring.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            ring.setColor(WidgetPrefs.ACCENTS[i]);
            if (i == sel) {
                ring.setStroke(dp(3), 0xFFFFFFFF);
            }
            dot.setBackground(ring);
        }
    }

    private int chipBg(boolean on) {
        if (on) return R.drawable.bg_chip_on;
        return R.drawable.bg_chip;
    }

    /** 强调色在浅底上偏淡，压深一档再当文字色用 */
    private int onAccentText() {
        int a = WidgetPrefs.accentColor(this);
        if (Theme.isDark(this)) {
            return a;
        }
        int r = (int) (((a >> 16) & 0xFF) * 0.62f);
        int g = (int) (((a >> 8) & 0xFF) * 0.62f);
        int b = (int) ((a & 0xFF) * 0.62f);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private void paintChipGroup(int[] ids, int selected) {
        for (int i = 0; i < ids.length; i++) {
            TextView tv = findViewById(ids[i]);
            if (tv == null) {
                continue;
            }
            boolean on = (i == selected);
            tv.setBackgroundResource(chipBg(on));
            tv.setTextColor(on ? onAccentText() : getColor(R.color.text_secondary));
        }
    }

    /** 尺寸档位（新布局里尺寸靠滑杆调，这里只留程序化入口） */
    private void preset(int id, final int w, final int h) {
        View v = findViewById(id);
        if (v == null) {
            return;
        }
        v.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                render();
            }
        });
    }

    private void applyStyle(int alpha, int cardAlpha, int font, int radius, int rootRadius, int cardH) {
        rowAlpha.bar.setProgress(alpha);
        rowCardAlpha.bar.setProgress(cardAlpha);
        rowFont.bar.setProgress(font - MIN_FONT);
        chosenCardRadius = nearestIndex(CARD_RADII, radius);
        chosenRootRadius = nearestIndex(ROOT_RADII, rootRadius);
        chosenCardHeight = nearestIndex(CARD_HEIGHTS, cardH);
        paintSegments();
        saveAndRender();
    }

    /**
     * 拖滑杆时的即时反馈：把值写进 prefs（预览要通过它渲染），但**不刷新桌面组件** ——
     * 桌面只在点「保存」时更新，这就是「改完要保存才生效」的实现方式。
     */
    /** 找数组里最接近的档位下标 */
    private static int nearestIndex(int[] arr, int v) {
        int best = 0;
        int diff = Integer.MAX_VALUE;
        for (int i = 0; i < arr.length; i++) {
            int d = Math.abs(arr[i] - v);
            if (d < diff) { diff = d; best = i; }
        }
        return best;
    }

    interface Setter {
        void set(int index, int value);
    }

    /** 绑定一组分段：点击写值并立刻重绘 */
    private void bindSegments(int[] ids, final int[] values, final Setter setter) {
        for (int i = 0; i < ids.length; i++) {
            final int idx = i;
            findViewById(ids[i]).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    setter.set(idx, values[idx]);
                    markDirty();
                    paintSegments();
                    saveAndRender();
                }
            });
        }
    }

    /** 重画三组分段的选中态 */
    private void paintSegments() {
        paintChipGroup(new int[]{R.id.r_root_0, R.id.r_root_1, R.id.r_root_2, R.id.r_root_3}, chosenRootRadius);
        paintChipGroup(new int[]{R.id.r_card_0, R.id.r_card_1, R.id.r_card_2, R.id.r_card_3}, chosenCardRadius);
        paintChipGroup(new int[]{R.id.h_auto, R.id.h_min, R.id.h_mid, R.id.h_max}, chosenCardHeight);
    }

    private void saveAndRender() {
        WidgetPrefs.save(this,
                MIN_ALPHA + rowAlpha.bar.getProgress(),
                rowCardAlpha.bar.getProgress(),
                MIN_FONT + rowFont.bar.getProgress(),
                CARD_RADII[chosenCardRadius],
                ROOT_RADII[chosenRootRadius],
                CARD_HEIGHTS[chosenCardHeight],
                WidgetPrefs.showUpcoming(this),
                WidgetPrefs.showPlace(this),
                WidgetPrefs.showTeacher(this));
        markDirty();
        render();
    }

    private void markDirty() {
        dirty = true;
        txtDirty.setVisibility(View.VISIBLE);
    }

    private void markClean() {
        dirty = false;
        txtDirty.setVisibility(View.GONE);
    }

    /**
     * 保存：刷桌面组件 + 提示哪些改动需要重新放置组件。
     *
     * 为什么要这个提示：RemoteViews 的行高、圆角裁剪这些，桌面已在用的组件
     * 不会重新走一遍 onUpdate，必须把组件拖掉再重新添加才吃到新值。
     */
    private void save() {
        ScheduleWidgetProvider.refreshAll(this);
        markClean();
        AppDialog.show(this, "已保存",
                "桌面组件已刷新。\n\n卡片高度、圆角这类属性需要重新添加组件才完全生效 —— "
                        + "长按桌面上的课表组件删掉，再重新拖一个出来即可。",
                "知道了", null, null, null);
    }

    /**
     * 离开页面：有未保存的改动就回滚到进入时的值。
     * 否则用户会以为「我刚才调了半天怎么没生效」。
     */
    @Override
    public void onBackPressed() {
        if (dirty) {
            AppDialog.show(this, "放弃未保存的修改？", "改动还没保存，返回会还原成进入时的样式。",
                    "放弃", new AppDialog.Action() {
                        @Override
                        public void run() {
                            rollback();
                            finish();
                        }
                    },
                    "继续编辑", null);
            return;
        }
        super.onBackPressed();
    }

    /** 回滚到进入页面时的样式 */
    private void rollback() {
        WidgetPrefs.save(this, origAlpha, origCardAlpha, origFont,
                origRadius, origRootRadius, origCardH,
                origUpcoming, origPlace, origTeacher);
        WidgetPrefs.sp(this).edit()
                .putInt("accent", origAccent)
                .putInt("refresh_mode", origRefresh)
                .putBoolean("hour24", origHour24)
                .putBoolean("showKind", origShowKind)
                .putBoolean("click_enabled", origClick)
                .apply();
        ScheduleWidgetProvider.refreshAll(this);
    }

    private void render() {
        rowAlpha.value.setText((MIN_ALPHA + rowAlpha.bar.getProgress()) + "%");
        rowCardAlpha.value.setText(rowCardAlpha.bar.getProgress() + "%");

        rowFont.value.setText((MIN_FONT + rowFont.bar.getProgress()) + "%");


        List<Event> events = EventStore.load(this);
        if (events.isEmpty()) {
            events = sampleEvents();
        }
        int wDp = PREVIEW_W_DP;
        int hDp = PREVIEW_H_DP;
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
