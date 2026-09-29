package com.rgcalendar;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;

import java.util.List;

/**
 * 极简卡片：按“日期分组标题 + 日程行”展示，最多 5 条日程，行数随卡片高度自适应。
 * 排在最前面的那条日程字号更大，用来突出重点。
 */
public class CompactWidgetProvider extends AppWidgetProvider {

    /** 最多显示最近 4 个日程：小卡片里宁可少而透气，也不要塞满 */
    private static final int MAX_EVENTS = 4;

    /**
     * 桌面回报的高度（OPTION_APPWIDGET_MIN_HEIGHT）是「视觉卡片高度」的标称值，
     * 并不是宿主槽位的真实高度（真机实测：标称 131dp，槽位实际约 171dp，卡片约 137dp）。
     * 所以只从标称高度里扣掉内容区到卡片边缘的少量留白，而不是扣掉全部的 padding，
     * 否则可用高度会被严重低估，日程条数排不进去。
     */
    private static final int HEIGHT_SLACK_DP = 8;
    /** 分组标题行高（dp）：10sp 正文约 13dp + 上下 4/4.8dp padding */
    private static final int HEADER_DP = 22;
    /** 普通日程行高（dp）：12.5sp 正文约 16dp + 上下 4/4.8dp padding */
    private static final int EVENT_DP = 25;
    /** 第一条日程行高（dp，字号更大）：16sp 正文约 20dp + 上下 4/4.8dp padding */
    private static final int FIRST_EVENT_DP = 29;

    private static final int[] LINE_IDS = {
            R.id.compact_line1, R.id.compact_line2, R.id.compact_line3, R.id.compact_line4,
            R.id.compact_line5, R.id.compact_line6, R.id.compact_line7, R.id.compact_line8,
    };

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        if (ids == null || ids.length == 0) return;
        HolidayRepository.refreshIfNeeded(ctx);
        for (int id : ids) {
            mgr.updateAppWidget(id, build(ctx, mgr, id));
        }
    }

    @Override
    public void onAppWidgetOptionsChanged(Context ctx, AppWidgetManager mgr, int id, Bundle options) {
        mgr.updateAppWidget(id, build(ctx, mgr, id));
    }

    static RemoteViews build(Context ctx, AppWidgetManager mgr, int appWidgetId) {
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget_compact);
        for (int id : LINE_IDS) {
            v.setViewVisibility(id, View.GONE);
            v.setOnClickPendingIntent(id, WidgetShared.openApp(ctx));
        }

        if (!CalendarRepository.hasPermission(ctx)) {
            showHint(v, ctx.getString(R.string.need_permission));
            return v;
        }

        List<WidgetRow> rows = WidgetShared.buildRows(ctx, MAX_EVENTS);
        if (rows.isEmpty()) {
            showHint(v, ctx.getString(R.string.no_events));
            return v;
        }

        int avail = widgetHeightDp(mgr, appWidgetId) - HEIGHT_SLACK_DP;
        int used = 0;
        int slot = 0;
        boolean firstEventDone = false;

        for (WidgetRow r : rows) {
            if (slot >= LINE_IDS.length) break;

            int rowDp = r.header ? HEADER_DP : (firstEventDone ? EVENT_DP : FIRST_EVENT_DP);
            if (slot > 0 && used + rowDp > avail) break;

            int viewId = LINE_IDS[slot];
            v.setViewVisibility(viewId, View.VISIBLE);

            if (r.header) {
                // 分组标题（日期）加粗。RemoteViews 没有 setTextStyle 这类接口，
                // 只能用 SpannableString 把粗体 span 一起塞进 setTextViewText。
                SpannableString bold = new SpannableString(r.text);
                bold.setSpan(new StyleSpan(Typeface.BOLD), 0, bold.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                v.setTextViewText(viewId, bold);
                v.setTextColor(viewId, WidgetShared.COLOR_HEADER);
                v.setTextViewTextSize(viewId, TypedValue.COMPLEX_UNIT_SP, WidgetShared.SIZE_HEADER);
            } else {
                v.setTextViewText(viewId, WidgetShared.eventText(r));
                v.setTextColor(viewId,
                        r.important ? WidgetShared.COLOR_IMPORTANT : WidgetShared.COLOR_TITLE);
                v.setTextViewTextSize(viewId, TypedValue.COMPLEX_UNIT_SP,
                        firstEventDone ? WidgetShared.SIZE_EVENT : WidgetShared.SIZE_FIRST_EVENT);
                firstEventDone = true;
            }
            used += rowDp;
            slot++;
        }

        // 最后一格如果正好是分组标题（下面已经排不下日程了），把它收掉，避免出现空标题
        if (slot > 0 && rows.get(slot - 1).header) {
            v.setViewVisibility(LINE_IDS[slot - 1], View.GONE);
        }
        return v;
    }

    private static void showHint(RemoteViews v, String text) {
        v.setViewVisibility(R.id.compact_line1, View.VISIBLE);
        v.setTextViewText(R.id.compact_line1, text);
        v.setTextColor(R.id.compact_line1, WidgetShared.COLOR_TIME);
        v.setTextViewTextSize(R.id.compact_line1, TypedValue.COMPLEX_UNIT_SP,
                WidgetShared.SIZE_HEADER);
    }

    /**
     * 只取 OPTION_APPWIDGET_MIN_HEIGHT：华为桌面回报的 min 是「视觉卡片高度」标称值
     * （真机 131dp，实测卡片 137dp），减去少量留白刚好等于内容区高度（实测 123dp）。
     * 不要换成 MAX_HEIGHT——本机回报 max=184dp，超过槽位真实高度 171dp，
     * 照它排版会把文字顶到白卡外面。
     */
    private static int widgetHeightDp(AppWidgetManager mgr, int appWidgetId) {
        Bundle opts = mgr.getAppWidgetOptions(appWidgetId);
        if (opts == null) return 110;
        int min = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0);
        return min > 0 ? min : 110;
    }
}