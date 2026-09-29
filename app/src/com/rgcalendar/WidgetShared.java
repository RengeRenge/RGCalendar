package com.rgcalendar;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import java.util.ArrayList;
import java.util.List;

/** 小工具公共逻辑：配色、字号、行数据、打开 App 的 PendingIntent、刷新全部小工具 */
public final class WidgetShared {

    /** 卡片底色改成了系统日历卡片同款的紫渐变（左下 #7485F7 → 右上 #BC93F8），
     *  所以文字全部换成浅色系：深色文字在紫底上几乎看不清。 */
    /** 分组标题（日期）：统一用同一种半透明白，作为弱化的分组标记，不跟日程抢注意力 */
    public static final int COLOR_HEADER = 0xCCFFFFFF;
    /** 重点日（节假日、调休、生日、纪念日）：紫底上红色会糊掉，改用浅黄加粗感 */
    public static final int COLOR_IMPORTANT = 0xFFFFF3B0;
    /** 普通日程标题 */
    public static final int COLOR_TITLE = 0xFFFFFFFF;
    /** 日程时间 / 空状态提示 */
    public static final int COLOR_TIME = 0xCCFFFFFF;

    /** 分组标题字号（小一号，让层级更清楚） */
    public static final float SIZE_HEADER = 10f;
    /** 普通日程字号 */
    public static final float SIZE_EVENT = 12.5f;
    /** 排在最前面的那条日程字号更大（比普通行突出，但不至于突兀） */
    public static final float SIZE_FIRST_EVENT = 16f;

    private static final int REQ_OPEN_APP = 1;
    /** 集合类小工具的点击模板必须与打开 App 用不同的 requestCode，
     *  否则请求码相同会复用同一个 PendingIntent，导致可变性不一致。 */
    private static final int REQ_TEMPLATE = 2;

    private WidgetShared() {}

    public static PendingIntent openApp(Context ctx) {
        Intent i = new Intent(ctx, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(ctx, REQ_OPEN_APP, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** 列表小工具的行点击模板：系统需要往里面填 extras，所以必须是 MUTABLE */
    public static PendingIntent openAppTemplate(Context ctx) {
        Intent i = new Intent(ctx, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(ctx, REQ_TEMPLATE, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
    }

    /**
     * 把日程整理成“分组标题 + 日程行”，同一天只在标题里出现一次日期。
     *
     * @param maxEvents 最多输出几条日程，&lt;=0 表示不限
     */
    public static List<WidgetRow> buildRows(Context ctx, int maxEvents) {
        List<WidgetRow> out = new ArrayList<WidgetRow>();
        long lastDay = Long.MIN_VALUE;
        int events = 0;
        for (AgendaItem it : CalendarRepository.load(ctx)) {
            if (maxEvents > 0 && events >= maxEvents) break;

            if (it.dayStart != lastDay) {
                lastDay = it.dayStart;
                WidgetRow h = new WidgetRow();
                h.header = true;
                // 相对日期（今天/明天/后天/N天后）只显示相对说法，不再重复日期和星期
                h.text = it.relative ? it.shortDay : it.dayLabel;
                out.add(h);
            }
            WidgetRow r = new WidgetRow();
            r.header = false;
            r.time = it.timeLabel;
            r.title = it.title;
            r.important = it.important;
            out.add(r);
            events++;
        }
        return out;
    }

    /** 日程行文字："09:00 晨会"；全天日程没有时间，只有标题 */
    public static String eventText(WidgetRow row) {
        String time = row.time == null ? "" : row.time;
        return time.length() == 0 ? row.title : time + "  " + row.title;
    }

    public static void updateAll(Context ctx) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);

        ComponentName listCn = new ComponentName(ctx, ListWidgetProvider.class);
        int[] listIds = mgr.getAppWidgetIds(listCn);
        if (listIds != null && listIds.length > 0) {
            for (int id : listIds) {
                mgr.updateAppWidget(id, ListWidgetProvider.build(ctx, id));
            }
            mgr.notifyAppWidgetViewDataChanged(listIds, R.id.widget_list);
        }

        ComponentName compactCn = new ComponentName(ctx, CompactWidgetProvider.class);
        int[] compactIds = mgr.getAppWidgetIds(compactCn);
        if (compactIds != null && compactIds.length > 0) {
            for (int id : compactIds) {
                mgr.updateAppWidget(id, CompactWidgetProvider.build(ctx, mgr, id));
            }
        }
    }
}