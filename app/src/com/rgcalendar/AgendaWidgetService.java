package com.rgcalendar;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.util.TypedValue;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import java.util.ArrayList;
import java.util.List;

/** 为列表小工具提供数据行（日期分组标题 + 日程行） */
public class AgendaWidgetService extends RemoteViewsService {

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new AgendaFactory(getApplicationContext(), intent);
    }
}

class AgendaFactory implements RemoteViewsService.RemoteViewsFactory {

    private final Context ctx;
    private final int appWidgetId;
    private List<WidgetRow> rows = new ArrayList<WidgetRow>();

    AgendaFactory(Context ctx, Intent intent) {
        this.ctx = ctx;
        this.appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID);
    }

    @Override
    public void onCreate() {
    }

    @Override
    public void onDataSetChanged() {
        rows = WidgetShared.buildRows(ctx, 0);
    }

    @Override
    public void onDestroy() {
        rows = new ArrayList<WidgetRow>();
    }

    @Override
    public int getCount() {
        return rows.size();
    }

    @Override
    public RemoteViews getViewAt(int position) {
        if (position < 0 || position >= rows.size()) return null;
        WidgetRow r = rows.get(position);

        Intent fill = new Intent();
        fill.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);

        if (r.header) {
            RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget_item_date);
            v.setTextViewText(R.id.item_date, r.text);
            v.setTextColor(R.id.item_date, WidgetShared.COLOR_HEADER);
            v.setTextViewTextSize(R.id.item_date, TypedValue.COMPLEX_UNIT_SP,
                    WidgetShared.SIZE_HEADER);
            v.setOnClickFillInIntent(R.id.item_date, fill);
            return v;
        }

        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget_item_event);
        v.setTextViewText(R.id.item_time, r.time);
        v.setTextViewText(R.id.item_title, r.title);
        v.setTextColor(R.id.item_title,
                r.important ? WidgetShared.COLOR_IMPORTANT : WidgetShared.COLOR_TITLE);
        v.setTextViewTextSize(R.id.item_title, TypedValue.COMPLEX_UNIT_SP,
                WidgetShared.SIZE_EVENT);
        v.setOnClickFillInIntent(R.id.item_root, fill);
        return v;
    }

    @Override
    public RemoteViews getLoadingView() {
        return null;
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }
}