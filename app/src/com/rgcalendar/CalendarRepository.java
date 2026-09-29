package com.rgcalendar;

import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/**
 * 通过标准 CalendarContract 读取华为日历（以及其它已同步日历）里的日程，
 * 并合并从网络取到的法定节假日 / 调休，统一输出成一串按时间排序的 AgendaItem。
 */
public final class CalendarRepository {

    /** 向外查询的天数：一年 */
    public static final int DAYS = 365;
    /** 30 天以内用“X天后”表述 */
    private static final int RELATIVE_LIMIT = 30;

    private static final long DAY_MS = 86400000L;
    private static final String[] WEEK = {"周日", "周一", "周二", "周三", "周四", "周五", "周六"};
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");
    private static final String[] IMPORTANT_WORDS = {"生日", "纪念日", "周年", "祭日", "忌日", "结婚"};

    private CalendarRepository() {}

    public static boolean hasPermission(Context ctx) {
        return ctx.checkSelfPermission(android.Manifest.permission.READ_CALENDAR)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** 今天 00:00（本地时区） */
    public static long windowStart() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    public static long windowEnd() {
        return windowStart() + DAYS * DAY_MS;
    }

    /** 只保留 VISIBLE=1 的日历账户，与系统日历显示保持一致 */
    private static Set<Long> visibleCalendarIds(Context ctx) {
        Set<Long> ids = new HashSet<Long>();
        Cursor cur = null;
        try {
            cur = ctx.getContentResolver().query(
                    CalendarContract.Calendars.CONTENT_URI,
                    new String[]{CalendarContract.Calendars._ID, CalendarContract.Calendars.VISIBLE},
                    null, null, null);
            while (cur != null && cur.moveToNext()) {
                if (cur.getInt(1) == 1) {
                    ids.add(Long.valueOf(cur.getLong(0)));
                }
            }
        } catch (Throwable t) {
            ids.clear();   // 账户表读不到就不过滤，保证仍有内容显示
        } finally {
            if (cur != null) cur.close();
        }
        return ids;
    }

    public static List<AgendaItem> load(Context ctx) {
        List<AgendaItem> out = new ArrayList<AgendaItem>();
        if (!hasPermission(ctx)) return out;

        long now = System.currentTimeMillis();
        long todayStart = windowStart();
        long end = windowEnd();

        Set<Long> visible = visibleCalendarIds(ctx);
        boolean filter = !visible.isEmpty();

        Uri.Builder b = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(b, todayStart);
        ContentUris.appendId(b, end);

        String[] proj = {
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.CALENDAR_ID,
                CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
        };
        String sel = CalendarContract.Instances.STATUS + " != "
                + CalendarContract.Instances.STATUS_CANCELED;

        Calendar display = Calendar.getInstance();
        Calendar localDay = Calendar.getInstance();
        Cursor cur = null;
        try {
            cur = ctx.getContentResolver().query(b.build(), proj, sel, null,
                    CalendarContract.Instances.BEGIN + " ASC");
            if (cur != null) {
                int iTitle = cur.getColumnIndex(CalendarContract.Instances.TITLE);
                int iBegin = cur.getColumnIndex(CalendarContract.Instances.BEGIN);
                int iEnd = cur.getColumnIndex(CalendarContract.Instances.END);
                int iAll = cur.getColumnIndex(CalendarContract.Instances.ALL_DAY);
                int iCal = cur.getColumnIndex(CalendarContract.Instances.CALENDAR_ID);
                int iCalName = cur.getColumnIndex(CalendarContract.Instances.CALENDAR_DISPLAY_NAME);

                while (cur.moveToNext()) {
                    if (filter && !visible.contains(Long.valueOf(cur.getLong(iCal)))) continue;

                    AgendaItem it = new AgendaItem();
                    it.title = cur.getString(iTitle);
                    it.begin = cur.getLong(iBegin);
                    it.end = cur.getLong(iEnd);
                    it.allDay = cur.getInt(iAll) == 1;
                    it.calendarName = cur.getString(iCalName);
                    if (it.title == null || it.title.trim().length() == 0) it.title = "(无标题)";
                    if (it.calendarName == null) it.calendarName = "";

                    if (it.end <= now) continue;   // 已经结束的日程不再占用卡片空间

                    fillDateFields(it, display, localDay, todayStart);
                    it.important = isImportant(it);
                    out.add(it);
                }
            }
        } catch (Throwable t) {
            // 局部失败就把已解析出来的部分返回，避免小工具整块空白
        } finally {
            if (cur != null) cur.close();
        }

        out.addAll(holidayItems(ctx, display, localDay, todayStart, end));

        Map<Long, String> holidayByDay = new HashMap<Long, String>();
        for (AgendaItem it : out) {
            if (it.dayHolidayName != null) {
                holidayByDay.put(Long.valueOf(it.dayStart), it.dayHolidayName);
            }
        }
        for (AgendaItem it : out) {
            if (it.dayHolidayName == null) {
                it.dayHolidayName = holidayByDay.get(Long.valueOf(it.dayStart));
            }
        }

        Collections.sort(out, new Comparator<AgendaItem>() {
            @Override
            public int compare(AgendaItem a, AgendaItem b) {
                if (a.dayStart != b.dayStart) return a.dayStart < b.dayStart ? -1 : 1;
                if (a.allDay != b.allDay) return a.allDay ? -1 : 1;
                if (a.begin != b.begin) return a.begin < b.begin ? -1 : 1;
                return 0;
            }
        });
        return out;
    }

    /** 今天还剩几个日程 */
    public static int countToday(List<AgendaItem> items) {
        long todayStart = windowStart();
        int n = 0;
        for (AgendaItem it : items) {
            if (it.dayStart == todayStart) n++;
        }
        return n;
    }

    /** 计算 dayStart / dayLabel / shortDay / timeLabel */
    private static void fillDateFields(AgendaItem it, Calendar display, Calendar localDay,
                                       long todayStart) {
        // 全天日程在数据库里按 UTC 零点存储，其余按本地时间存储
        display.setTimeZone(it.allDay ? UTC : TimeZone.getDefault());
        display.setTimeInMillis(it.begin);
        int y = display.get(Calendar.YEAR);
        int m = display.get(Calendar.MONTH);
        int d = display.get(Calendar.DAY_OF_MONTH);
        int dow = display.get(Calendar.DAY_OF_WEEK);

        localDay.clear();
        localDay.set(y, m, d, 0, 0, 0);
        it.dayStart = localDay.getTimeInMillis();

        String md = String.format(Locale.CHINA, "%02d-%02d", m + 1, d);
        String week = WEEK[dow - 1];
        String rel = relativeName(it.dayStart, todayStart);

        it.relative = rel != null;
        it.shortDay = rel != null ? rel : md;
        it.dayLabel = (rel != null ? rel + " " : "") + md + " " + week;

        if (it.allDay) {
            it.timeLabel = "";               // 全天不再显示“全天”二字
        } else {
            it.timeLabel = String.format(Locale.CHINA, "%02d:%02d",
                    display.get(Calendar.HOUR_OF_DAY), display.get(Calendar.MINUTE));
        }
    }

    /** 今天/明天/后天/N天后；超过 30 天返回 null */
    private static String relativeName(long dayStart, long todayStart) {
        long diff = (dayStart - todayStart) / DAY_MS;
        if (diff < 0 || diff > RELATIVE_LIMIT) return null;
        if (diff == 0) return "今天";
        if (diff == 1) return "明天";
        if (diff == 2) return "后天";
        return diff + "天后";
    }

    private static boolean isImportant(AgendaItem it) {
        String title = it.title == null ? "" : it.title;
        for (String w : IMPORTANT_WORDS) {
            if (title.contains(w)) return true;
        }
        String cal = it.calendarName == null ? "" : it.calendarName.toLowerCase(Locale.ROOT);
        return cal.contains("生日") || cal.contains("birthday") || cal.contains("纪念日");
    }

    /** 把窗口内的法定假日 / 调休补成“全天日程” */
    private static List<AgendaItem> holidayItems(Context ctx, Calendar display, Calendar localDay,
                                                 long todayStart, long end) {
        Map<String, HolidayRepository.Day> map = HolidayRepository.days(ctx);
        List<AgendaItem> list = new ArrayList<AgendaItem>();
        if (map.isEmpty()) return list;

        Calendar utc = Calendar.getInstance(UTC);
        for (Map.Entry<String, HolidayRepository.Day> e : map.entrySet()) {
            String[] parts = e.getKey().split("-");
            if (parts.length != 3) continue;
            int y = parseInt(parts[0]);
            int m = parseInt(parts[1]) - 1;
            int d = parseInt(parts[2]);

            localDay.clear();
            localDay.set(y, m, d, 0, 0, 0);
            if (localDay.getTimeInMillis() < todayStart || localDay.getTimeInMillis() >= end) continue;

            // 全天日程统一用 UTC 零点表示，和系统写入的数据保持一致
            utc.clear();
            utc.set(y, m, d, 0, 0, 0);

            HolidayRepository.Day hd = e.getValue();
            AgendaItem it = new AgendaItem();
            it.begin = utc.getTimeInMillis();
            it.end = it.begin + DAY_MS;
            it.allDay = true;
            it.calendarName = "节假日";
            it.title = hd.name.length() > 0 ? hd.name : (hd.offDay ? "放假" : "调休上班");
            it.important = true;
            it.dayHolidayName = hd.offDay
                    ? (hd.name.length() > 0 ? hd.name : "放假") : "补班";
            fillDateFields(it, display, localDay, todayStart);
            list.add(it);
        }
        return list;
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Throwable t) {
            return 0;
        }
    }
}