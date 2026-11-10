package com.aliboder.classwidget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 时间线主界面 —— 尺寸全部按小程序 rpx 还原（750rpx = 屏宽）。
 *
 * 卡片结构与 view-timeline 的 .card 一一对应：
 *   14rpx 左边距 → 78rpx 时间列 → 8rpx → 12rpx 色条 → 8rpx → 内容(paddingLeft 已含)
 *   合计 120rpx，与 .card 的 padding-left: 120rpx 一致。
 */
public class MainActivity extends Activity {

    private static final int REQ_PICK = 1001;
    private static final long DAY_MS = 24L * 3600L * 1000L;

    private static final int C_TITLE = 0xFFE9EDF0;
    private static final int C_META = 0xFFA8B2BC;
    private static final int C_DIM = 0xFF6B7480;
    private static final int C_GREEN = 0xFF3FD3AC;

    private final SimpleDateFormat dayKeyFmt = new SimpleDateFormat("yyyyMMdd", Locale.CHINA);
    private final SimpleDateFormat dateFmt = new SimpleDateFormat("M月d日", Locale.CHINA);
    private final SimpleDateFormat weekFmt = new SimpleDateFormat("EEE", Locale.CHINA);
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.CHINA);
    private final SimpleDateFormat stampFmt = new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA);

    private TextView status;
    private TextView statusSub;
    private LinearLayout list;
    private TextView listTitle;

    private boolean showPast = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        status = findViewById(R.id.status);
        statusSub = findViewById(R.id.status_sub);
        list = findViewById(R.id.list);
        listTitle = findViewById(R.id.list_title);

        TextView subtitle = findViewById(R.id.subtitle);
        try {
            String vn = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            subtitle.setText("导入 .ics，桌面小部件自动更新 · v" + vn);
        } catch (Exception ignored) {
        }

        handleViewIntent(getIntent());

        findViewById(R.id.btn_pick).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openPicker();
            }
        });
        findViewById(R.id.btn_pair).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPairDialog();
            }
        });
        findViewById(R.id.btn_preview).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, PreviewActivity.class));
            }
        });
        findViewById(R.id.btn_refresh).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int[] ids = AppWidgetManager.getInstance(MainActivity.this).getAppWidgetIds(
                        new ComponentName(MainActivity.this, ScheduleWidgetProvider.class));
                if (ids.length == 0) {
                    Toast.makeText(MainActivity.this, R.string.no_widget, Toast.LENGTH_SHORT).show();
                    return;
                }
                ScheduleWidgetProvider.refreshAll(MainActivity.this);
                Toast.makeText(MainActivity.this, "已刷新 " + ids.length + " 个组件", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    /* ---------------- 导入 ---------------- */

    private void openPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "text/calendar", "text/x-vcalendar", "application/ics",
                "application/octet-stream", "text/plain"
        });
        try {
            startActivityForResult(intent, REQ_PICK);
        } catch (Exception e) {
            Toast.makeText(this, "没有可用的文件选择器", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK || resultCode != RESULT_OK || data == null) {
            return;
        }
        Uri uri = data.getData();
        if (uri != null) {
            importUri(uri, uri.getLastPathSegment() == null ? "schedule.ics" : uri.getLastPathSegment());
        }
    }

    private void handleViewIntent(Intent intent) {
        if (intent == null || !Intent.ACTION_VIEW.equals(intent.getAction())) {
            return;
        }
        Uri uri = intent.getData();
        if (uri != null) {
            importUri(uri, uri.getLastPathSegment() == null ? "schedule.ics" : uri.getLastPathSegment());
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleViewIntent(intent);
    }

    private void importUri(final Uri uri, final String name) {
        status.setText("正在解析…");
        statusSub.setText(name);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    InputStream in = getContentResolver().openInputStream(uri);
                    if (in == null) {
                        throw new IllegalStateException("无法读取文件");
                    }
                    final List<Event> events = IcsParser.parse(in);
                    in.close();
                    if (events.isEmpty()) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                status.setText("没解析到日程");
                                statusSub.setText("确认这是课表导出的 .ics 文件");
                            }
                        });
                        return;
                    }
                    EventStore.save(MainActivity.this, events, name);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this,
                                    "已导入 " + events.size() + " 条日程", Toast.LENGTH_SHORT).show();
                            render();
                            ScheduleWidgetProvider.refreshAll(MainActivity.this);
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            status.setText("解析失败");
                            statusSub.setText(String.valueOf(e.getMessage()));
                        }
                    });
                }
            }
        }).start();
    }

    /* ---------------- 配对码导入 ---------------- */

    /** 输入 6 位配对码 → 后台拉取 → 解析 → 保存 → 刷新组件 */
    private void showPairDialog() {
        PairCodeDialog dialog = new PairCodeDialog(this, new PairCodeDialog.OnSubmit() {
            @Override
            public void onSubmit(String code) {
                importByPairCode(code);
            }
        });
        dialog.show();
    }

    private void importByPairCode(final String code) {
        status.setText("正在获取配对码 " + code + " …");
        statusSub.setText("从服务器拉取课表");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final PairClient.Result r = PairClient.fetch(code);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!r.ok) {
                            status.setText("获取失败");
                            statusSub.setText(r.message);
                            Toast.makeText(MainActivity.this, r.message, Toast.LENGTH_LONG).show();
                            return;
                        }
                        parseAndSave(r.ics, r.name == null ? "配对码-课表.ics" : r.name);
                    }
                });
            }
        }).start();
    }

    /** 把 ics 文本解析入库（与文件导入共用同一条落库路径） */
    private void parseAndSave(final String icsText, final String name) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<Event> events = IcsParser.parse(icsText);
                    if (events.isEmpty()) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                status.setText("没解析到日程");
                                statusSub.setText("配对码对应的课表是空的");
                            }
                        });
                        return;
                    }
                    EventStore.save(MainActivity.this, events, name);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this,
                                    "已导入 " + events.size() + " 条日程", Toast.LENGTH_SHORT).show();
                            render();
                            ScheduleWidgetProvider.refreshAll(MainActivity.this);
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            status.setText("解析失败");
                            statusSub.setText(String.valueOf(e.getMessage()));
                        }
                    });
                }
            }
        }).start();
    }

    /* ---------------- 渲染 ---------------- */

    private void render() {
        List<Event> events = EventStore.load(this);
        list.removeAllViews();
        if (events.isEmpty()) {
            status.setText("还没有数据");
            statusSub.setText("选一个课表导出的 .ics 文件，桌面小部件会自动更新");
            listTitle.setVisibility(View.GONE);
            return;
        }

        String source = EventStore.source(this);
        long at = EventStore.importedAt(this);
        status.setText("已载入 " + events.size() + " 条日程");
        String sub = source.isEmpty() ? "" : "来源 " + source;
        if (at > 0) {
            sub = sub.isEmpty() ? "更新于 " + stampFmt.format(new Date(at))
                    : sub + " · 更新于 " + stampFmt.format(new Date(at));
        }
        statusSub.setText(sub.isEmpty() ? "长按桌面空白处可添加小部件" : sub);
        listTitle.setVisibility(View.VISIBLE);

        buildTimeline(events);
    }

    private void buildTimeline(List<Event> events) {
        // 时间线按开始时间升序（IcsParser 已排过，这里再兜一次，防止手工改数据后错乱）
        List<Event> sorted = new ArrayList<>(events);
        java.util.Collections.sort(sorted, new java.util.Comparator<Event>() {
            @Override
            public int compare(Event a, Event b) {
                return Long.compare(a.start, b.start);
            }
        });
        events = sorted;

        long now = System.currentTimeMillis();
        long todayStart = startOfDay(now);
        String todayKey = dayKeyFmt.format(new Date(now));

        List<Event> past = new ArrayList<>();
        List<Event> upcoming = new ArrayList<>();
        for (Event e : events) {
            if (startOfDay(e.start) < todayStart) {
                past.add(e);
            } else {
                upcoming.add(e);
            }
        }

        // 「过去 N 天」折叠条：展开后变成「收起 ▴」
        if (!past.isEmpty()) {
            int days = (int) ((todayStart - startOfDay(past.get(0).start)) / DAY_MS);
            TextView toggle = new TextView(this);
            toggle.setText(showPast
                    ? "收起过去 " + Math.max(1, days) + " 天  ▴"
                    : "过去 " + Math.max(1, days) + " 天  ▾");
            toggle.setTextSize(TypedValue.COMPLEX_UNIT_PX, rpx(24));
            toggle.setTextColor(C_DIM);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, rpx(66));
            toggle.setLayoutParams(tlp);
            toggle.setGravity(Gravity.CENTER_VERTICAL);
            toggle.setPadding(rpx(32), 0, rpx(32), 0);
            toggle.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showPast = !showPast;
                    render();
                }
            });
            list.addView(toggle);
        }

        List<Event> ordered = new ArrayList<>();
        if (showPast) {
            ordered.addAll(past);
        }
        ordered.addAll(upcoming);

        String lastKey = null;
        int shown = 0;

        for (Event ev : ordered) {
            if (shown >= 220) {
                break;
            }
            String key = dayKeyFmt.format(new Date(ev.start));
            boolean isToday = key.equals(todayKey);

            if (!key.equals(lastKey)) {
                if (lastKey != null) {
                    list.addView(new View(this), gap(rpx(16)));
                }
                lastKey = key;
                int count = 0;
                for (Event x : ordered) {
                    if (dayKeyFmt.format(new Date(x.start)).equals(key)) {
                        count++;
                    }
                }
                list.addView(dayHeader(ev.start, isToday, count));
            }

            list.addView(tlCard(ev, now));
            shown++;

            // 今天这一段：在「最后一条真的已经过去」的卡之后插「现在」标线
            if (isToday) {
                boolean isLastPast = true;
                for (Event next : ordered) {
                    if (next == ev) {
                        continue;
                    }
                    if (dayKeyFmt.format(new Date(next.start)).equals(todayKey)
                            && next.end <= now && next.start > ev.start) {
                        isLastPast = false;
                        break;
                    }
                }
                if (isLastPast && ev.end <= now) {
                    list.addView(nowLine(now));
                }
            }
        }

        list.addView(diagnosticCard(events));
    }

    /** 段头：11月8日 | 周日 [今天] ………… 2 节 */
    private View dayHeader(long millis, boolean today, int count) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(rpx(32), rpx(24), rpx(32), rpx(16));

        TextView date = new TextView(this);
        date.setText(dateFmt.format(new Date(millis)));
        date.setTextSize(TypedValue.COMPLEX_UNIT_PX, rpx(34));
        date.setTextColor(today ? C_GREEN : C_TITLE);
        date.setTypeface(null, Typeface.BOLD);
        date.setIncludeFontPadding(false);
        row.addView(date);

        TextView sep = new TextView(this);
        sep.setText("|");
        sep.setTextSize(TypedValue.COMPLEX_UNIT_PX, rpx(22));
        sep.setTextColor(today ? 0x883FD3AC : C_DIM);
        sep.setIncludeFontPadding(false);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        slp.leftMargin = rpx(8);
        slp.rightMargin = rpx(8);
        sep.setLayoutParams(slp);
        row.addView(sep);

        TextView weekday = new TextView(this);
        weekday.setText(weekFmt.format(new Date(millis)));
        weekday.setTextSize(TypedValue.COMPLEX_UNIT_PX, rpx(30));
        weekday.setTextColor(today ? 0xCC3FD3AC : C_META);
        weekday.setIncludeFontPadding(false);
        row.addView(weekday);

        if (today) {
            TextView chip = smallTag("今天", C_GREEN, R.drawable.tag_on, 20, 12);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, rpx(32));
            clp.leftMargin = rpx(10);
            chip.setLayoutParams(clp);
            row.addView(chip);
        }

        View flex = new View(this);
        flex.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        row.addView(flex);

        TextView cnt = new TextView(this);
        cnt.setText(count + " 节");
        cnt.setTextSize(TypedValue.COMPLEX_UNIT_PX, rpx(24));
        cnt.setTextColor(C_DIM);
        cnt.setIncludeFontPadding(false);
        row.addView(cnt);

        return row;
    }

    /** 现在标线：绿点 + 横线 + 「现在 19:26」 */
    private View nowLine(long now) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(rpx(32), rpx(12), rpx(32), rpx(12));

        View dot = new View(this);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(C_GREEN);
        dot.setBackground(dotBg);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(rpx(12), rpx(12));
        dlp.rightMargin = rpx(12);
        dot.setLayoutParams(dlp);
        row.addView(dot);

        View line = new View(this);
        line.setBackgroundColor(0x593FD3AC);
        line.setLayoutParams(new LinearLayout.LayoutParams(0, rpx(2), 1f));
        row.addView(line);

        TextView txt = new TextView(this);
        txt.setText("现在 " + timeFmt.format(new Date(now)));
        txt.setTextSize(TypedValue.COMPLEX_UNIT_PX, rpx(22));
        txt.setTextColor(C_GREEN);
        txt.setIncludeFontPadding(false);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tlp.leftMargin = rpx(16);
        txt.setLayoutParams(tlp);
        row.addView(txt);

        return row;
    }

    /**
     * 一张课程卡。结构照 .card：
     *   paddingLeft 14rpx（时间列起点）
     *   时间列 78rpx / marginRight 8rpx
     *   色条 12rpx（上下各留 12rpx）/ marginRight 8rpx
     *   内容（剩余宽度，右侧 padding 18rpx）
     */
    private View tlCard(Event e, long now) {
        boolean ongoing = now >= e.start && now < e.end;
        boolean past = now >= e.end;

        FrameLayout wrap = new FrameLayout(this);
        wrap.setBackgroundResource(R.drawable.card_tl);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        wlp.leftMargin = rpx(32);
        wlp.rightMargin = rpx(32);
        wlp.bottomMargin = rpx(16);
        wrap.setLayoutParams(wlp);

        // 底层：进行中的课按时间进度从左往右填（照 .card 的 linear-gradient 口径）
        if (ongoing && e.end > e.start) {
            int pct = (int) Math.max(0, Math.min(100, (now - e.start) * 100L / (e.end - e.start)));
            LinearLayout fill = new LinearLayout(this);
            fill.setOrientation(LinearLayout.HORIZONTAL);

            View used = new View(this);
            GradientDrawable usedBg = new GradientDrawable();
            usedBg.setShape(GradientDrawable.RECTANGLE);
            float rr = rpx(24);
            usedBg.setCornerRadii(new float[]{rr, rr, 0, 0, 0, 0, rr, rr});
            int base = CourseColors.color(e.title);
            usedBg.setColor(Color.argb(0x26, Color.red(base), Color.green(base), Color.blue(base)));
            used.setBackground(usedBg);

            used.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, pct)));
            fill.addView(used);
            View rest = new View(this);
            rest.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, 100 - pct)));
            fill.addView(rest);

            wrap.addView(fill, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        }

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(rpx(14), rpx(16), rpx(18), rpx(16));
        card.setMinimumHeight(rpx(112));
        wrap.addView(card, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

        // ---- 时间列 ----
        LinearLayout timeCol = new LinearLayout(this);
        timeCol.setOrientation(LinearLayout.VERTICAL);
        timeCol.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams tclp = new LinearLayout.LayoutParams(rpx(78), LinearLayout.LayoutParams.WRAP_CONTENT);
        tclp.rightMargin = rpx(8);
        timeCol.setLayoutParams(tclp);

        TextView start = new TextView(this);
        start.setText(e.allDay ? "全天" : timeFmt.format(new Date(e.start)));
        start.setTextSize(TypedValue.COMPLEX_UNIT_PX, rpx(22));
        start.setTextColor(0xFFFFFFFF);
        start.setTypeface(null, Typeface.BOLD);
        start.setGravity(Gravity.CENTER);
        start.setIncludeFontPadding(false);
        start.setSingleLine(true);
        start.setBackgroundResource(CourseColors.res(e.title));
        start.setPadding(rpx(8), rpx(4), rpx(8), rpx(4));
        timeCol.addView(start, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, rpx(34)));

        if (!e.allDay) {
            TextView end = new TextView(this);
            end.setText(timeFmt.format(new Date(e.end)));
            end.setTextSize(TypedValue.COMPLEX_UNIT_PX, rpx(22));
            end.setTextColor(C_META);
            end.setGravity(Gravity.CENTER);
            end.setIncludeFontPadding(false);
            end.setSingleLine(true);
            end.setPadding(rpx(8), rpx(4), rpx(8), rpx(4));
            LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, rpx(34));
            elp.topMargin = rpx(2);
            end.setLayoutParams(elp);
            timeCol.addView(end);
        }
        card.addView(timeCol);

        // ---- 课程色条 ----
        View strip = new View(this);
        GradientDrawable stripBg = new GradientDrawable();
        stripBg.setCornerRadius(rpx(6));
        stripBg.setColor(past ? 0x668A929C : CourseColors.color(e.title));
        strip.setBackground(stripBg);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(rpx(12), LinearLayout.LayoutParams.MATCH_PARENT);
        slp.topMargin = rpx(2);
        slp.bottomMargin = rpx(2);
        slp.rightMargin = rpx(8);
        strip.setLayoutParams(slp);
        card.addView(strip);

        // ---- 内容 ----
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER_VERTICAL);
        body.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText(e.title);
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX, rpx(34));
        title.setTextColor(past ? 0xFF8A929C : C_TITLE);
        title.setTypeface(null, Typeface.BOLD);
        title.setIncludeFontPadding(false);
        title.setMaxLines(1);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(title);

        if (past || ongoing) {
            head.addView(smallTag(ongoing ? "进行中" : "已结束",
                    ongoing ? C_GREEN : C_META,
                    ongoing ? R.drawable.tag_on : R.drawable.tag_past, 20, 12));
        }
        head.addView(smallTag("课程", 0xFF9FE8D1, R.drawable.tag_on, 22, 18));
        body.addView(head);

        String place = e.place == null ? "" : e.place.trim();
        String teacher = e.teacher == null ? "" : e.teacher.trim();
        if (!place.isEmpty() || !teacher.isEmpty()) {
            LinearLayout meta = new LinearLayout(this);
            meta.setOrientation(LinearLayout.HORIZONTAL);
            meta.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            mlp.topMargin = rpx(8);
            meta.setLayoutParams(mlp);

            if (!place.isEmpty()) {
                meta.addView(icon(R.drawable.ic_pin, 0));
                meta.addView(metaText(place, 0));
            }
            if (!teacher.isEmpty()) {
                meta.addView(icon(R.drawable.ic_person, rpx(16)));
                meta.addView(metaText(teacher, 0));
            }
            body.addView(meta);
        }

        card.addView(body);
        return wrap;
    }

    /** 小标签（定高 + 居中 + 左右内边距），对应 .tg */
    private TextView smallTag(String text, int color, int bgRes, int fontRpx, int padRpx) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, rpx(fontRpx));
        tv.setTextColor(color);
        tv.setBackgroundResource(bgRes);
        tv.setGravity(Gravity.CENTER);
        tv.setIncludeFontPadding(false);
        tv.setSingleLine(true);
        tv.setPadding(rpx(padRpx), 0, rpx(padRpx), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, rpx(fontRpx + 12));
        lp.leftMargin = rpx(8);
        tv.setLayoutParams(lp);
        return tv;
    }

    private ImageView icon(int res, int leftMargin) {
        ImageView iv = new ImageView(this);
        iv.setImageResource(res);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(rpx(26), rpx(26));
        lp.leftMargin = leftMargin;
        iv.setLayoutParams(lp);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        return iv;
    }

    private TextView metaText(String text, int leftMargin) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, rpx(24));
        tv.setTextColor(C_META);
        tv.setIncludeFontPadding(false);
        tv.setSingleLine(true);
        tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = leftMargin == 0 ? rpx(6) : leftMargin;
        tv.setLayoutParams(lp);
        return tv;
    }

    private LinearLayout.LayoutParams gap(int height) {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height);
    }

    /* ---------------- 诊断 ---------------- */

    private View diagnosticCard(List<Event> events) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundResource(R.drawable.card_tl);
        box.setPadding(rpx(24), rpx(20), rpx(24), rpx(20));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = rpx(32);
        lp.leftMargin = rpx(32);
        lp.rightMargin = rpx(32);
        box.setLayoutParams(lp);

        TextView head = new TextView(this);
        head.setText("解析诊断");
        head.setTextSize(TypedValue.COMPLEX_UNIT_PX, rpx(22));
        head.setTextColor(C_DIM);
        box.addView(head);

        Event first = events.get(0);
        TextView body = new TextView(this);
        String raw = first.rawDesc == null ? "" : first.rawDesc.trim();
        if (raw.isEmpty()) {
            raw = "（这条 ics 里没有 DESCRIPTION）";
        }
        body.setText("课程：" + first.title
                + "\n地点：" + (first.place == null || first.place.isEmpty() ? "—" : first.place)
                + "\n教师：" + (first.teacher == null || first.teacher.isEmpty() ? "—「没解析到」" : first.teacher)
                + "\n原始描述：" + raw);
        body.setTextSize(TypedValue.COMPLEX_UNIT_PX, rpx(22));
        body.setTextColor(C_META);
        body.setLineSpacing(rpx(4), 1f);
        body.setTextIsSelectable(true);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        blp.topMargin = rpx(8);
        body.setLayoutParams(blp);
        box.addView(body);
        return box;
    }

    /* ---------------- 工具 ---------------- */

    /** 小程序 rpx → 像素（750rpx = 屏幕宽） */
    private int rpx(float v) {
        return Math.round(v * getResources().getDisplayMetrics().widthPixels / 750f);
    }

    private long startOfDay(long millis) {
        Calendar c = Calendar.getInstance(Locale.CHINA);
        c.setTimeInMillis(millis);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }
}
