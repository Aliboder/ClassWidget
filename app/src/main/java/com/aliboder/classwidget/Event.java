package com.aliboder.classwidget;

/** 一条已展开的日程（周课表已被拆成具体日期）。 */
public class Event {
    public String title = "";
    public String place = "";
    public String teacher = "";
    /** 原始 DESCRIPTION（诊断用，用来确认导出方到底写了什么） */
    public String rawDesc = "";
    /** 类型标签：课程 / 考试 / 日程（来自 ics 的 CATEGORIES；缺省当课程） */
    public String kind = "课程";
    public long start;
    public long end;
    public boolean allDay;
}
