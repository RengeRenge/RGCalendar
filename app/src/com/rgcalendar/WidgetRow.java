package com.rgcalendar;

/** 小工具列表里的一行：日期分隔行或日程行 */
public final class WidgetRow {
    public boolean header;
    /** header=true 时使用 */
    public String text;
    public String time;
    public String title;
    /** 需要突出显示 */
    public boolean important;
}