package com.rgcalendar;

/** 单条日程（已按天归组所需的信息预计算好） */
public final class AgendaItem {
    public String title;
    public String calendarName;
    public long begin;
    public long end;
    public boolean allDay;
    /** 该日程所属“天”的本地 00:00 毫秒数，用于分组 */
    public long dayStart;
    /** 形如 "今天 09-28 周一" / "3天后 10-01 周四" */
    public String dayLabel;
    /** 形如 "今天" / "明天" / "3天后" / "11-01"，用于紧凑卡片 */
    public String shortDay;
    /** shortDay 是否为"今天/明天/后天/N天后"这类相对日期 */
    public boolean relative;
    /** 形如 "08:00"；全天日程为空串 */
    public String timeLabel;
    /** 该天若是节假日/调休，这里是标注文字（如 "国庆节"、"补班"），否则为 null */
    public String dayHolidayName;
    /** 需要突出显示（节假日、调休、生日、纪念日） */
    public boolean important;
}