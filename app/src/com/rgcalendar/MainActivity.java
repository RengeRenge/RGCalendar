package com.rgcalendar;

import android.Manifest;
import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** 主界面：申请日历权限 + 展示未来 7 天完整日程，并顺带刷新桌面小工具 */
public class MainActivity extends Activity {

    private static final int REQ_PERMISSION = 1001;

    /** 构建标记：用来确认手机上装的确实是这一版（临时，确认后删掉） */
    private static final String BUILD_TAG = "v23";

    private TextView header;
    private ArrayAdapter<String> adapter;
    private final List<String> lines = new ArrayList<String>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), 0);

        header = new TextView(this);
        header.setTextSize(14);
        header.setTextColor(0xFF1F6FEB);
        root.addView(header, matchWrap());

        Button refresh = new Button(this);
        refresh.setText("刷新");
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!CalendarRepository.hasPermission(MainActivity.this)) {
                    requestPermissions(new String[]{Manifest.permission.READ_CALENDAR}, REQ_PERMISSION);
                } else {
                    render();
                }
            }
        });
        root.addView(refresh, matchWrap());

        ListView list = new ListView(this);
        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, lines);
        list.setAdapter(adapter);
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);

        if (!CalendarRepository.hasPermission(this)) {
            requestPermissions(new String[]{Manifest.permission.READ_CALENDAR}, REQ_PERMISSION);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        render();
    }

    private void render() {
        lines.clear();
        HolidayRepository.refreshIfNeeded(this);

        if (!CalendarRepository.hasPermission(this)) {
            header.setText("未授予日历权限");
            lines.add("点上方「刷新」并在系统弹窗里选择「允许」，才能读取日历");
        } else {
            List<AgendaItem> items = CalendarRepository.load(this);
            header.setText("接下来 · " + BUILD_TAG);

            lines.add(widgetSizeInfo());
            if (items.isEmpty()) {
                lines.add("暂无日程");
            } else {
                long lastDay = Long.MIN_VALUE;
                for (AgendaItem it : items) {
                    if (it.dayStart != lastDay) {
                        lastDay = it.dayStart;
                        String h = "── " + it.dayLabel;
                        if (it.dayHolidayName != null) h += " · " + it.dayHolidayName;
                        lines.add(h + " ──");
                    }
                    lines.add((it.important ? "★ " : "   ")
                            + (it.allDay ? "全天" : it.timeLabel)
                            + "  " + it.title + "  @" + it.calendarName);
                }
            }
        }

        adapter.notifyDataSetChanged();
        WidgetShared.updateAll(this);
    }

    /** 回显桌面小工具实际拿到的尺寸，便于按真机数据微调卡片大小 */
    private String widgetSizeInfo() {
        AppWidgetManager mgr = AppWidgetManager.getInstance(this);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(this, CompactWidgetProvider.class));
        if (ids == null || ids.length == 0) {
            return "卡片小工具：未添加到桌面";
        }
        Bundle o = mgr.getAppWidgetOptions(ids[0]);
        if (o == null) return "卡片小工具：尺寸未知";
        return "卡片小工具 min " + o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
                + "×" + o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
                + " dp / max " + o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
                + "×" + o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT) + " dp";
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }
}