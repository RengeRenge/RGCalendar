package com.rgcalendar;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViews;

/** 可滚动列表小工具：按日期分组展示接下来的全部日程 */
public class ListWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        if (ids == null || ids.length == 0) return;
        HolidayRepository.refreshIfNeeded(ctx);
        for (int id : ids) {
            mgr.updateAppWidget(id, build(ctx, id));
        }
        mgr.notifyAppWidgetViewDataChanged(ids, R.id.widget_list);
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        super.onReceive(ctx, intent);
        if (AppWidgetManager.ACTION_APPWIDGET_UPDATE.equals(intent.getAction())) {
            AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
            int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, ListWidgetProvider.class));
            if (ids != null && ids.length > 0) {
                mgr.notifyAppWidgetViewDataChanged(ids, R.id.widget_list);
            }
        }
    }

    static RemoteViews build(Context ctx, int appWidgetId) {
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget_list);

        Intent svc = new Intent(ctx, AgendaWidgetService.class);
        svc.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        svc.setData(Uri.parse(svc.toUri(Intent.URI_INTENT_SCHEME)));
        v.setRemoteAdapter(R.id.widget_list, svc);
        v.setEmptyView(R.id.widget_list, R.id.widget_empty);

        boolean granted = CalendarRepository.hasPermission(ctx);
        v.setTextViewText(R.id.widget_empty,
                ctx.getString(granted ? R.string.no_events : R.string.need_permission));

        v.setPendingIntentTemplate(R.id.widget_list, WidgetShared.openAppTemplate(ctx));
        v.setOnClickPendingIntent(R.id.widget_empty, WidgetShared.openApp(ctx));
        return v;
    }
}