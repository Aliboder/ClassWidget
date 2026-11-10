package com.aliboder.classwidget;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 桌面小部件 —— 和小程序「时间线」一致的卡片：
 *
 *   11月9日 | 周一                                    2 节
 *   ┌──────────────────────────────────────────────┐
 *   │ [14:40]  ┃  运输组织学              [课程]    │
 *   │ [16:15]  ┃  📍 教1-424       👤 王小霞        │
 *   └──────────────────────────────────────────────┘
 *
 * 开始时间 = 课程色胶囊白字；结束时间 = 灰胶囊；中间的短竖线连接两者。
 * 4×2 放 2 个事件，4×4 放 5 个，字号随卡片高度微调。
 */
public class ScheduleWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_REFRESH = "com.aliboder.classwidget.ACTION_REFRESH";

    private static final int MAX_CARDS = 6;
    private static final long DAY_MS = 24L * 3600L * 1000L;

    private static final int[] ROW_IDS = new int[MAX_CARDS];
    private static final int[] CARD_IDS = new int[MAX_CARDS];
    private static final int[] FRAMES = new int[MAX_CARDS];
    private static final int[] STARTS = new int[MAX_CARDS];
    private static final int[] ENDS = new int[MAX_CARDS];
    private static final int[] DASHES = new int[MAX_CARDS];
    private static final int[] STRIPS = new int[MAX_CARDS];
    private static final int[] TITLES = new int[MAX_CARDS];
    private static final int[] TAGS = new int[MAX_CARDS];
    private static final int[] METAS = new int[MAX_CARDS];
    private static final int[] BARS = new int[MAX_CARDS];
    private static final int[] PINS = new int[MAX_CARDS];
    private static final int[] PEOPLE = new int[MAX_CARDS];
    private static final int[] TEACHERS = new int[MAX_CARDS];

    static {
        int[] rows = {R.id.w_row1, R.id.w_row2, R.id.w_row3, R.id.w_row4, R.id.w_row5, R.id.w_row6};
        int[] cards = {R.id.w_row1_card, R.id.w_row2_card, R.id.w_row3_card,
                R.id.w_row4_card, R.id.w_row5_card, R.id.w_row6_card};
        int[] frames = {R.id.w_row1_frame, R.id.w_row2_frame, R.id.w_row3_frame,
                R.id.w_row4_frame, R.id.w_row5_frame, R.id.w_row6_frame};
        for (int i = 0; i < MAX_CARDS; i++) {
            ROW_IDS[i] = rows[i];
            CARD_IDS[i] = cards[i];
            FRAMES[i] = frames[i];
        }
        STARTS[0] = R.id.w_row1_start; STARTS[1] = R.id.w_row2_start; STARTS[2] = R.id.w_row3_start;
        STARTS[3] = R.id.w_row4_start; STARTS[4] = R.id.w_row5_start; STARTS[5] = R.id.w_row6_start;
        ENDS[0] = R.id.w_row1_end; ENDS[1] = R.id.w_row2_end; ENDS[2] = R.id.w_row3_end;
        ENDS[3] = R.id.w_row4_end; ENDS[4] = R.id.w_row5_end; ENDS[5] = R.id.w_row6_end;
        DASHES[0] = R.id.w_row1_dash; DASHES[1] = R.id.w_row2_dash; DASHES[2] = R.id.w_row3_dash;
        DASHES[3] = R.id.w_row4_dash; DASHES[4] = R.id.w_row5_dash; DASHES[5] = R.id.w_row6_dash;
        STRIPS[0] = R.id.w_row1_strip; STRIPS[1] = R.id.w_row2_strip; STRIPS[2] = R.id.w_row3_strip;
        STRIPS[3] = R.id.w_row4_strip; STRIPS[4] = R.id.w_row5_strip; STRIPS[5] = R.id.w_row6_strip;
        TITLES[0] = R.id.w_row1_title; TITLES[1] = R.id.w_row2_title; TITLES[2] = R.id.w_row3_title;
        TITLES[3] = R.id.w_row4_title; TITLES[4] = R.id.w_row5_title; TITLES[5] = R.id.w_row6_title;
        TAGS[0] = R.id.w_row1_tag; TAGS[1] = R.id.w_row2_tag; TAGS[2] = R.id.w_row3_tag;
        TAGS[3] = R.id.w_row4_tag; TAGS[4] = R.id.w_row5_tag; TAGS[5] = R.id.w_row6_tag;
        METAS[0] = R.id.w_row1_meta; METAS[1] = R.id.w_row2_meta; METAS[2] = R.id.w_row3_meta;
        METAS[3] = R.id.w_row4_meta; METAS[4] = R.id.w_row5_meta; METAS[5] = R.id.w_row6_meta;
        BARS[0] = R.id.w_row1_bar; BARS[1] = R.id.w_row2_bar; BARS[2] = R.id.w_row3_bar;
        BARS[3] = R.id.w_row4_bar; BARS[4] = R.id.w_row5_bar; BARS[5] = R.id.w_row6_bar;
        PINS[0] = R.id.w_row1_pin; PINS[1] = R.id.w_row2_pin; PINS[2] = R.id.w_row3_pin;
        PINS[3] = R.id.w_row4_pin; PINS[4] = R.id.w_row5_pin; PINS[5] = R.id.w_row6_pin;
        PEOPLE[0] = R.id.w_row1_person; PEOPLE[1] = R.id.w_row2_person; PEOPLE[2] = R.id.w_row3_person;
        PEOPLE[3] = R.id.w_row4_person; PEOPLE[4] = R.id.w_row5_person; PEOPLE[5] = R.id.w_row6_person;
        TEACHERS[0] = R.id.w_row1_teacher; TEACHERS[1] = R.id.w_row2_teacher; TEACHERS[2] = R.id.w_row3_teacher;
        TEACHERS[3] = R.id.w_row4_teacher; TEACHERS[4] = R.id.w_row5_teacher; TEACHERS[5] = R.id.w_row6_teacher;
    }

    private static final class Item {
        Event event;
        String dayPrefix = "";
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        for (int id : appWidgetIds) {
            manager.updateAppWidget(id, buildViews(context, manager, id));
        }
        scheduleNextRefresh(context);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager,
                                          int appWidgetId, Bundle newOptions) {
        manager.updateAppWidget(appWidgetId, buildViews(context, manager, appWidgetId));
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (intent != null && ACTION_REFRESH.equals(intent.getAction())) {
            refreshAll(context);
        }
    }

    public static void refreshAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName cn = new ComponentName(context, ScheduleWidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(cn);
        for (int id : ids) {
            manager.updateAppWidget(id, buildViews(context, manager, id));
        }
        scheduleNextRefresh(context);
    }

    private static RemoteViews buildViews(Context context, AppWidgetManager manager, int widgetId) {
        Bundle opts = manager == null ? null : manager.getAppWidgetOptions(widgetId);
        int heightDp = opts == null ? 158 : opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 158);
        int widthDp = opts == null ? 340 : opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 340);
        WidgetPrefs.recordSize(context, widthDp, heightDp);
        return build(context, heightDp, EventStore.load(context));
    }

    /** 这个高度放几个事件 */
    /** 标签文字：进行中优先，否则显示类型（课程 / 考试 / 日程） */
    private static String kindLabel(Event e) {
        String k = e.kind == null ? "" : e.kind.trim();
        return k.isEmpty() ? "课程" : k;
    }

    /** 图标尺寸档位：跟着字号走，平板/大字号时不会一直是个小点 */
    private static int iconTier(float scale) {
        if (scale < 1.0f) return 1;    // 10dp
        if (scale < 1.15f) return 2;   // 12dp
        if (scale < 1.35f) return 3;   // 14dp
        if (scale < 1.6f) return 4;    // 16dp
        return 5;                       // 18dp
    }

    private static final int[] PIN_RES = {
            R.drawable.ic_pin_1, R.drawable.ic_pin_2, R.drawable.ic_pin_3,
            R.drawable.ic_pin_4, R.drawable.ic_pin_5
    };
    private static final int[] PERSON_RES = {
            R.drawable.ic_person_1, R.drawable.ic_person_2, R.drawable.ic_person_3,
            R.drawable.ic_person_4, R.drawable.ic_person_5
    };

    public static int rowsFor(int heightDp) {
        int rows = Math.round((heightDp - 46f) / 52f);
        return Math.max(1, Math.min(MAX_CARDS, rows));
    }

    /**
     * 字号按「高度档位」取，而不是按高度连续缩放。
     *
     * 为什么：桌面上小部件的真实高度 = ceil(需求高度 / 格子高度) × 格子高度 − 内边距，
     * 各机型格子高度不同（官方口径：假设格子 70×80dp，需求 110dp 的部件最终高
     * Math.ceil(110/80)×80 − padding），所以 OPTION_APPWIDGET_MIN_HEIGHT 和真实高度能差 10~20dp。
     * 连续缩放会让同样一个 4×2 在不同机器上字大小不同；分档后 ±20dp 内观感一致。
     */
    public static float sizeBucket(int heightDp) {
        if (heightDp < 200) {
            return 1.0f;
        }
        if (heightDp < 300) {
            return 1.15f;
        }
        return 1.3f;
    }

    public static float scaleFor(int heightDp) {
        return sizeBucket(heightDp);
    }

    public static RemoteViews build(Context context, int heightDp, List<Event> allRaw) {
        // 按开始时间排序（与时间线同口径，防止数据顺序异常）
        List<Event> all = new ArrayList<>(allRaw);
        java.util.Collections.sort(all, new java.util.Comparator<Event>() {
            @Override
            public int compare(Event a, Event b) {
                return Long.compare(a.start, b.start);
            }
        });
        RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.widget_schedule);

        int fixedCardH = WidgetPrefs.cardHeight(context);
        int rows = fixedCardH > 0
                ? Math.max(1, Math.min(MAX_CARDS, (int) ((heightDp - 46f) / fixedCardH)))
                : rowsFor(heightDp);
        int alpha = WidgetPrefs.alpha(context);
        int cardAlpha = WidgetPrefs.cardAlpha(context);
        int radius = WidgetPrefs.radius(context);
        int rootRadius = WidgetPrefs.rootRadius(context);
        boolean showPlace = WidgetPrefs.showPlace(context);
        boolean showTeacher = WidgetPrefs.showTeacher(context);
        float cardH = fixedCardH > 0 ? fixedCardH : (heightDp - 46f) / Math.max(1, rows);
        float baseScale = fixedCardH > 0
                ? Math.max(0.85f, Math.min(1.3f, fixedCardH / 56f))
                : sizeBucket(heightDp);
        float scale = baseScale * (WidgetPrefs.fontPercent(context) / 100f);
        // 卡片高度固定，字号不能无限放大：按内容 42dp × 缩放 + 上下内边距 12dp 反算上限
        scale = Math.min(scale, Math.max(0.8f, (cardH - 12f) / 42f));

        rv.setInt(R.id.w_frame, "setBackgroundResource",
                alpha <= 0 ? R.drawable.round_blank : WidgetBgs.round(rootRadius));
        rv.setInt(R.id.w_root, "setBackgroundColor", argb(alpha, 0x15, 0x15, 0x1A));
        float titleSp = 14.5f * scale;
        float pillSp = 9.5f * scale;
        float metaSp = 10.5f * scale;

        long now = System.currentTimeMillis();
        long dayStart = startOfDay(now);

        boolean upcoming = WidgetPrefs.showUpcoming(context);
        List<Item> items = new ArrayList<>();
        int todayCount = collectDay(items, all, now, dayStart, 0, "");
        int firstDayCount = todayCount;
        long headerDay = dayStart;

        if (upcoming) {
            // 今天排不满就往后接：明天 / 后天 / …… 最多再看 6 天
            for (int d = 1; d <= 6 && items.size() < MAX_CARDS; d++) {
                String prefix = d == 1 ? "明天" : (d == 2 ? "后天" : (d + " 天后"));
                collectDay(items, all, now, dayStart, d, prefix);
            }
            if (todayCount == 0 && !items.isEmpty()) {
                headerDay = startOfDay(items.get(0).event.start);
                firstDayCount = 0;
                for (Item it : items) {
                    if (it.event.start < headerDay + DAY_MS && it.event.start >= headerDay) {
                        firstDayCount++;
                    } else {
                        break;
                    }
                }
            }
        }

        rv.setTextViewText(R.id.w_date, fmt(headerDay, "M月d日"));
        rv.setTextViewText(R.id.w_weekday, fmt(headerDay, "EEE"));
        rv.setTextViewTextSize(R.id.w_date, TypedValue.COMPLEX_UNIT_DIP, 15f * scale);
        rv.setTextViewTextSize(R.id.w_weekday, TypedValue.COMPLEX_UNIT_DIP, 12.5f * scale);
        rv.setTextViewTextSize(R.id.w_sep, TypedValue.COMPLEX_UNIT_DIP, 12f * scale);
        rv.setTextViewTextSize(R.id.w_week, TypedValue.COMPLEX_UNIT_DIP, 12f * scale);
        String hint = items.isEmpty() ? "" : countdownHint(all, now);
        if (hint.isEmpty() && !items.isEmpty()) {
            hint = firstDayCount + " 节";
        }
        rv.setTextViewText(R.id.w_week, hint);

        if (items.isEmpty()) {
            rv.setViewVisibility(R.id.w_empty, View.VISIBLE);
            rv.setTextViewText(R.id.w_empty,
                    all.isEmpty() ? "点这里导入 .ics 课表" : (upcoming ? "近期没有课" : "今天没有课了"));
            rv.setTextViewTextSize(R.id.w_empty, TypedValue.COMPLEX_UNIT_DIP, 12f * scale);
            for (int i = 0; i < MAX_CARDS; i++) {
                rv.setViewVisibility(ROW_IDS[i], View.GONE);
            }
            bindClick(context, rv);
            return rv;
        }

        rv.setViewVisibility(R.id.w_empty, View.GONE);

        for (int i = 0; i < MAX_CARDS; i++) {
            if (i < rows && i < items.size()) {
                Item it = items.get(i);
                Event e = it.event;
                boolean ongoing = now >= e.start && now < e.end;
                boolean ended = now >= e.end;

                rv.setViewVisibility(ROW_IDS[i], View.VISIBLE);
                if (Build.VERSION.SDK_INT >= 31) {
                    rv.setViewLayoutHeight(ROW_IDS[i], cardH, TypedValue.COMPLEX_UNIT_DIP);
                }
                rv.setInt(FRAMES[i], "setBackgroundResource",
                        cardAlpha <= 0 ? R.drawable.round_blank : WidgetBgs.round(radius));
                rv.setInt(CARD_IDS[i], "setBackgroundColor", argb(cardAlpha, 0x1E, 0x22, 0x26));
                rv.setViewVisibility(STARTS[i], View.VISIBLE);

                rv.setTextViewTextSize(STARTS[i], TypedValue.COMPLEX_UNIT_DIP, pillSp);
                rv.setTextViewTextSize(ENDS[i], TypedValue.COMPLEX_UNIT_DIP, pillSp);
                rv.setTextViewTextSize(TITLES[i], TypedValue.COMPLEX_UNIT_DIP, titleSp);
                rv.setTextViewTextSize(METAS[i], TypedValue.COMPLEX_UNIT_DIP, metaSp);
                rv.setTextViewTextSize(TEACHERS[i], TypedValue.COMPLEX_UNIT_DIP, metaSp);
                rv.setTextViewTextSize(TAGS[i], TypedValue.COMPLEX_UNIT_DIP, 9.5f * scale);

                int pill = CourseColors.res(e.title);
                rv.setInt(STARTS[i], "setBackgroundResource", pill);
                rv.setInt(STRIPS[i], "setBackgroundResource", pill);
                rv.setTextViewText(STARTS[i], e.allDay ? "全天" : fmt(e.start, "HH:mm"));

                boolean hasEnd = !e.allDay && e.end > e.start;
                rv.setViewVisibility(ENDS[i], hasEnd ? View.VISIBLE : View.GONE);
                rv.setViewVisibility(DASHES[i], hasEnd ? View.VISIBLE : View.GONE);
                if (hasEnd) {
                    rv.setTextViewText(ENDS[i], fmt(e.end, "HH:mm"));
                }

                rv.setTextViewText(TITLES[i], e.title);
                rv.setTextViewText(TAGS[i], ongoing ? "进行中" : (WidgetPrefs.showKind(context) ? kindLabel(e) : ""));
                int pct = 0;
                if (ongoing && e.end > e.start) {
                    pct = (int) Math.max(0, Math.min(100, (now - e.start) * 100L / (e.end - e.start)));
                } else if (ended) {
                    pct = 100;
                }
                rv.setProgressBar(BARS[i], 100, pct, false);
                rv.setViewVisibility(BARS[i], (ongoing || ended) ? View.VISIBLE : View.GONE);

                String place = e.place == null ? "" : e.place.trim();
                String shown = it.dayPrefix.isEmpty() ? place
                        : (place.isEmpty() ? it.dayPrefix : it.dayPrefix + " · " + place);
                rv.setTextViewText(METAS[i], showPlace ? shown : (it.dayPrefix.isEmpty() ? "" : it.dayPrefix));
                if (!showPlace) {
                    shown = it.dayPrefix;
                }
                int tier = iconTier(scale);
                rv.setImageViewResource(PINS[i], PIN_RES[tier - 1]);
                rv.setImageViewResource(PEOPLE[i], PERSON_RES[tier - 1]);
                rv.setViewVisibility(PINS[i], !shown.isEmpty() ? View.VISIBLE : View.GONE);

                String teacher = e.teacher == null ? "" : e.teacher.trim();
                rv.setTextViewText(TEACHERS[i], showTeacher ? teacher : "");
                rv.setViewVisibility(PEOPLE[i],
                        showTeacher && !teacher.isEmpty() ? View.VISIBLE : View.GONE);
            } else {
                rv.setViewVisibility(ROW_IDS[i], View.GONE);
            }
        }

        bindClick(context, rv);
        return rv;
    }

    /**
     * 「距下课 43 分钟 / 距下节课 25 分钟」——与小程序 utils/timeline.countdownOf 同口径：
     * ① 有进行中的取最先结束的那节；② 否则取最近未开始的一节；③ 都没有 → 空。
     */
    private static String countdownHint(List<Event> all, long now) {
        if (all == null || all.isEmpty()) {
            return "";
        }
        Event ongoing = null;
        for (Event e : all) {
            if (now >= e.start && now < e.end) {
                if (ongoing == null || e.end < ongoing.end) {
                    ongoing = e;
                }
            }
        }
        if (ongoing != null) {
            long left = Math.max(0, (ongoing.end - now) / 60000L);
            return left < 1 ? "即将下课" : "距下课 " + countdownText(left);
        }
        Event next = null;
        for (Event e : all) {
            if (e.start > now) {
                if (next == null || e.start < next.start) {
                    next = e;
                }
            }
        }
        if (next != null) {
            long left = Math.max(0, (next.start - now) / 60000L);
            return left < 1 ? "即将上课" : "距上课 " + countdownText(left);
        }
        return "";
    }

    /** 与小程序 countdownText 一致：< 60 分钟写分钟，否则「x 时 y 分」 */
    private static String countdownText(long min) {
        if (min < 60) {
            return min + " 分钟";
        }
        long h = min / 60;
        long r = min % 60;
        return r > 0 ? h + " 时 " + r + " 分" : h + " 时";
    }

    private static int argb(int percent, int r, int g, int b) {
        int a = Math.round(Math.max(0, Math.min(100, percent)) * 255f / 100f);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int collectDay(List<Item> out, List<Event> all, long now, long dayStart,
                                  int offsetDays, String prefix) {
        long from = dayStart + offsetDays * DAY_MS;
        long to = from + DAY_MS;
        int n = 0;
        for (Event e : all) {
            if (e.start >= from && e.start < to && e.end > now) {
                Item it = new Item();
                it.event = e;
                it.dayPrefix = prefix == null ? "" : prefix;
                out.add(it);
                n++;
            }
        }
        return n;
    }

    private static String fmt(long millis, String pattern) {
        return new SimpleDateFormat(pattern, Locale.CHINA).format(new Date(millis));
    }

    private static void bindClick(Context context, RemoteViews rv) {
        if (!WidgetPrefs.clickEnabled(context)) {
            return;
        }
        Intent open = new Intent(context, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(context, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        rv.setOnClickPendingIntent(R.id.w_date, pi);
        rv.setOnClickPendingIntent(R.id.w_empty, pi);
        for (int row : ROW_IDS) {
            rv.setOnClickPendingIntent(row, pi);
        }
    }

    static void scheduleNextRefresh(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            return;
        }
        Intent intent = new Intent(context, ScheduleWidgetProvider.class);
        intent.setAction(ACTION_REFRESH);
        PendingIntent pi = PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        long now = System.currentTimeMillis();
        List<Event> events = EventStore.load(context);
        if (events.isEmpty()) {
            am.cancel(pi);
            return;
        }

        // ① 事件时刻：下一节课开始 / 某节课结束 / 跨天
        long next = 0;
        boolean ongoing = false;
        for (Event e : events) {
            if (e.start <= now && now < e.end) {
                ongoing = true;
            }
            if (e.start > now && (next == 0 || e.start < next)) {
                next = e.start;
            }
            if (e.end > now && (next == 0 || e.end < next)) {
                next = e.end;
            }
        }
        // 今天的课上完后如果不跨天刷新，组件会一直停在「今天没有课了」
        long midnight = startOfDay(now) + DAY_MS + 30000L;
        if (next == 0 || midnight < next) {
            next = midnight;
        }

        // ② 按刷新模式叠加「分钟级」刷新
        int mode = WidgetPrefs.refreshMode(context);
        boolean tickEveryMinute = mode == WidgetPrefs.REFRESH_FREQUENT
                || (mode == WidgetPrefs.REFRESH_SMART && ongoing);
        if (tickEveryMinute) {
            long tick = now + 60000L;
            if (tick < next) {
                next = tick;
            }
        }

        if (next - now < 30000L) {
            next = now + 30000L;
        }
        am.set(AlarmManager.RTC_WAKEUP, next, pi);
    }

    private static long startOfDay(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }
}
