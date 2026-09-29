package com.rgcalendar;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 中国法定节假日 / 调休数据。
 * 系统日历里读不到（华为把节假日做成了“日历”App 的内置数据，不暴露给 CalendarContract），
 * 所以改为联网获取后落盘缓存，离线时继续用缓存。
 */
public final class HolidayRepository {

    private static final String PREF = "holiday_cache";
    private static final String FMT_TIMOR = "T|";
    private static final String FMT_HOLIDAY_CN = "C|";
    /** 拉取失败后多久内不再重试 */
    private static final long RETRY_MS = 6 * 3600_000L;
    private static final int HTTP_TIMEOUT_MS = 10000;

    private static final Object LOCK = new Object();
    private static boolean fetching = false;
    private static Map<String, Day> cache;
    private static int cacheYear = -1;

    private HolidayRepository() {}

    /** 某一天的节假日信息 */
    public static final class Day {
        public String name = "";
        /** true=放假，false=调休上班 */
        public boolean offDay;
    }

    /** key 形如 "2026-10-01"；覆盖今年和明年，只读缓存不联网 */
    public static Map<String, Day> days(Context ctx) {
        int year = Calendar.getInstance().get(Calendar.YEAR);
        synchronized (LOCK) {
            if (cache != null && cacheYear == year) return cache;
            Map<String, Day> merged = new HashMap<String, Day>();
            merged.putAll(readCache(ctx, year));
            merged.putAll(readCache(ctx, year + 1));
            cache = merged;
            cacheYear = year;
            return cache;
        }
    }

    /** 今年/明年缺数据时后台拉一次；成功后刷新桌面小工具 */
    public static void refreshIfNeeded(final Context ctx) {
        final int year = Calendar.getInstance().get(Calendar.YEAR);
        final SharedPreferences sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);

        final List<Integer> missing = new ArrayList<Integer>();
        for (int y = year; y <= year + 1; y++) {
            if (!sp.contains(key(y)) && !recentlyTried(sp, y)) {
                missing.add(Integer.valueOf(y));
            }
        }
        if (missing.isEmpty()) return;

        synchronized (LOCK) {
            if (fetching) return;
            fetching = true;
        }
        for (Integer y : missing) {
            sp.edit().putLong("try" + y, System.currentTimeMillis()).apply();
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean got = false;
                for (Integer boxed : missing) {
                    if (fetchYear(ctx, sp, boxed.intValue())) got = true;
                }
                synchronized (LOCK) {
                    fetching = false;
                    cache = null;
                    cacheYear = -1;
                }
                if (got) {
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            WidgetShared.updateAll(ctx);
                        }
                    });
                }
            }
        }, "holiday-fetch").start();
    }

    /** 抓一年；解析出内容才落盘，避免把“该年数据还没发布”的空响应缓存住 */
    private static boolean fetchYear(Context ctx, SharedPreferences sp, int year) {
        String body = null;
        String fmt = null;
        try {
            body = httpGet("https://timor.tech/api/holiday/year/" + year);
            fmt = FMT_TIMOR;
        } catch (Throwable ignored) {
        }
        if (body == null) {
            try {
                body = httpGet("https://cdn.jsdelivr.net/gh/NateScarlet/"
                        + "holiday-cn@master/" + year + ".json");
                fmt = FMT_HOLIDAY_CN;
            } catch (Throwable ignored) {
            }
        }
        if (body == null) return false;

        Map<String, Day> probe = new HashMap<String, Day>();
        try {
            if (FMT_TIMOR.equals(fmt)) {
                parseTimor(body, year, probe);
            } else {
                parseHolidayCn(body, probe);
            }
        } catch (Throwable t) {
            probe.clear();
        }
        if (probe.isEmpty()) return false;

        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit().putString(key(year), fmt + body).apply();
        return true;
    }

    private static boolean recentlyTried(SharedPreferences sp, int year) {
        return System.currentTimeMillis() - sp.getLong("try" + year, 0L) < RETRY_MS;
    }

    private static String key(int year) {
        return "y" + year;
    }

    private static Map<String, Day> readCache(Context ctx, int year) {
        Map<String, Day> map = new HashMap<String, Day>();
        SharedPreferences sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String raw = sp.getString(key(year), null);
        if (raw == null) return map;
        try {
            if (raw.startsWith(FMT_TIMOR)) {
                parseTimor(raw.substring(FMT_TIMOR.length()), year, map);
            } else if (raw.startsWith(FMT_HOLIDAY_CN)) {
                parseHolidayCn(raw.substring(FMT_HOLIDAY_CN.length()), map);
            }
        } catch (Throwable t) {
            map.clear();
        }
        return map;
    }

    /** timor.tech: {"code":0,"holiday":{"01-01":{"holiday":true,"name":"元旦"},...}} */
    private static void parseTimor(String json, int year, Map<String, Day> out) throws Exception {
        JSONObject root = new JSONObject(json);
        JSONObject holiday = root.optJSONObject("holiday");
        if (holiday == null) return;
        Iterator<String> it = holiday.keys();
        while (it.hasNext()) {
            String mmdd = it.next();
            JSONObject o = holiday.optJSONObject(mmdd);
            if (o == null) continue;
            String name = o.optString("name", "");
            if (name.length() == 0) continue;
            Day d = new Day();
            d.name = name;
            d.offDay = o.optBoolean("holiday", false);
            out.put(year + "-" + mmdd, d);
        }
    }

    /** holiday-cn: {"year":2026,"days":[{"date":"2026-01-01","name":"元旦","isOffDay":true},...]} */
    private static void parseHolidayCn(String json, Map<String, Day> out) throws Exception {
        JSONObject root = new JSONObject(json);
        JSONArray days = root.optJSONArray("days");
        if (days == null) return;
        for (int i = 0; i < days.length(); i++) {
            JSONObject o = days.optJSONObject(i);
            if (o == null) continue;
            String date = o.optString("date", "");
            if (date.length() == 0) continue;
            Day d = new Day();
            d.name = o.optString("name", "");
            d.offDay = o.optBoolean("isOffDay", false);
            out.put(date, d);
        }
    }

    private static String httpGet(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setConnectTimeout(HTTP_TIMEOUT_MS);
            conn.setReadTimeout(HTTP_TIMEOUT_MS);
            conn.setRequestMethod("GET");
            conn.setInstanceFollowRedirects(true);
            int code = conn.getResponseCode();
            if (code != 200) throw new IllegalStateException("HTTP " + code);
            StringBuilder sb = new StringBuilder();
            BufferedReader r = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), "UTF-8"));
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line);
            }
            r.close();
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }
}