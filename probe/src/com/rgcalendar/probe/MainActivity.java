package com.rgcalendar.probe;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * 探针：回答两个问题
 *  1) 这台荣耀/华为手机的鸿蒙 API 版本是多少（getprop）
 *  2) 安卓应用能否读到系统日历里已有的日程（CalendarContract）
 */
public class MainActivity extends Activity {

    private static final int REQ = 1001;

    private TextView out;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (10 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        Button read = new Button(this);
        read.setText("读取");
        read.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!granted()) {
                    requestPermissions(new String[]{Manifest.permission.READ_CALENDAR}, REQ);
                } else {
                    render();
                }
            }
        });
        bar.addView(read);

        Button copy = new Button(this);
        copy.setText("复制结果");
        copy.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null && out != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("probe", out.getText().toString()));
                    toast("已复制");
                }
            }
        });
        bar.addView(copy);

        root.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        out = new TextView(this);
        out.setTypeface(Typeface.MONOSPACE);
        out.setTextSize(11f);
        out.setTextIsSelectable(true);

        ScrollView sc = new ScrollView(this);
        sc.addView(out);
        root.addView(sc, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);

        if (!granted()) {
            requestPermissions(new String[]{Manifest.permission.READ_CALENDAR}, REQ);
        } else {
            render();
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (code == REQ) {
            boolean ok = results != null && results.length > 0
                    && results[0] == PackageManager.PERMISSION_GRANTED;
            toast(ok ? "已授权日历读取" : "未授权日历读取");
            render();
        }
    }

    private boolean granted() {
        return checkSelfPermission(Manifest.permission.READ_CALENDAR)
                == PackageManager.PERMISSION_GRANTED;
    }

    // ------------------------------------------------------------------ 报告

    private void render() {
        StringBuilder sb = new StringBuilder();
        sb.append("========== 1. 设备 / 系统 ==========\n");
        sb.append("Build.MODEL      = ").append(Build.MODEL).append('\n');
        sb.append("Build.BRAND      = ").append(Build.BRAND).append('\n');
        sb.append("Android RELEASE  = ").append(Build.VERSION.RELEASE).append('\n');
        sb.append("Android SDK_INT  = ").append(Build.VERSION.SDK_INT).append('\n');
        sb.append("Build.DISPLAY    = ").append(Build.DISPLAY).append('\n');

        String[] keys = {
                "hw_sc.build.os.apiversion",
                "hw_sc.build.os.version",
                "hw_sc.build.os.releasetype",
                "hw_sc.build.platform.version",
                "hw_sc.build.build.version",
                "hw_sc.build.os.extapi",
                "ro.build.version.emui",
                "ro.build.version.harmony",
                "ro.build.version.release",
                "ro.build.version.sdk",
                "ro.product.model",
                "ro.build.display.id",
                "ro.build.version.incremental",
        };
        sb.append("--- getprop 关键项 ---\n");
        appendProps(sb, sh("getprop"), keys);

        sb.append("\n========== 2. 权限 ==========\n");
        sb.append("READ_CALENDAR    = ").append(granted() ? "GRANTED" : "DENIED").append('\n');

        sb.append("\n========== 3. 日历账户 Calendars ==========\n");
        queryCalendars(sb);

        sb.append("\n========== 4. Events 表总数 ==========\n");
        queryEventCount(sb);

        sb.append("\n========== 5. 未来 7 天日程 Instances ==========\n");
        queryInstances(sb);

        sb.append("\n（把结果复制发回给我）");
        out.setText(sb.toString());
    }

    private void queryCalendars(StringBuilder sb) {
        Cursor c = null;
        try {
            String[] proj = {
                    CalendarContract.Calendars._ID,
                    CalendarContract.Calendars.ACCOUNT_NAME,
                    CalendarContract.Calendars.ACCOUNT_TYPE,
                    CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                    CalendarContract.Calendars.VISIBLE,
                    CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                    CalendarContract.Calendars.SYNC_EVENTS,
            };
            c = getContentResolver().query(
                    CalendarContract.Calendars.CONTENT_URI, proj, null, null,
                    CalendarContract.Calendars._ID + " ASC");
            if (c == null) {
                sb.append("query 返回 null —— Provider 不存在或不可用\n");
                return;
            }
            sb.append("count = ").append(c.getCount()).append('\n');
            while (c.moveToNext()) {
                sb.append("  id=").append(c.getString(0))
                  .append("  acct=").append(c.getString(1))
                  .append("  type=").append(c.getString(2))
                  .append('\n');
                sb.append("      display=").append(c.getString(3))
                  .append("  visible=").append(c.getInt(4))
                  .append("  access=").append(c.getInt(5))
                  .append("  sync=").append(c.getInt(6))
                  .append('\n');
            }
        } catch (Throwable t) {
            sb.append("EXCEPTION: ").append(describe(t)).append('\n');
        } finally {
            if (c != null) c.close();
        }
    }

    private void queryEventCount(StringBuilder sb) {
        Cursor c = null;
        try {
            c = getContentResolver().query(
                    CalendarContract.Events.CONTENT_URI,
                    new String[]{CalendarContract.Events._ID}, null, null, null);
            if (c == null) {
                sb.append("query 返回 null —— Provider 不存在或不可用\n");
                return;
            }
            sb.append("全库 Events 总数 = ").append(c.getCount()).append('\n');
        } catch (Throwable t) {
            sb.append("EXCEPTION: ").append(describe(t)).append('\n');
        } finally {
            if (c != null) c.close();
        }
    }

    private void queryInstances(StringBuilder sb) {
        Cursor c = null;
        try {
            Calendar start = Calendar.getInstance();
            start.set(Calendar.HOUR_OF_DAY, 0);
            start.set(Calendar.MINUTE, 0);
            start.set(Calendar.SECOND, 0);
            start.set(Calendar.MILLISECOND, 0);
            long begin = start.getTimeInMillis();
            long end = begin + 8L * 24L * 60L * 60L * 1000L - 1L;

            Uri.Builder b = CalendarContract.Instances.CONTENT_URI.buildUpon();
            ContentUris.appendId(b, begin);
            ContentUris.appendId(b, end);

            String[] proj = {
                    CalendarContract.Instances.TITLE,
                    CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.END,
                    CalendarContract.Instances.ALL_DAY,
                    CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
                    CalendarContract.Instances.EVENT_ID,
            };
            c = getContentResolver().query(b.build(), proj, null, null,
                    CalendarContract.Instances.BEGIN + " ASC");
            if (c == null) {
                sb.append("query 返回 null —— Provider 不存在或不可用\n");
                return;
            }
            SimpleDateFormat f = new SimpleDateFormat("MM-dd HH:mm", Locale.US);
            sb.append("窗口: ").append(f.format(new Date(begin)))
              .append("  ~  ").append(f.format(new Date(end))).append('\n');
            sb.append("count = ").append(c.getCount()).append('\n');
            int n = 0;
            while (c.moveToNext() && n < 30) {
                sb.append("  ").append(f.format(new Date(c.getLong(1))))
                  .append("  ").append(c.getInt(3) == 1 ? "[全天] " : "")
                  .append(c.getString(0))
                  .append("  @").append(c.getString(4))
                  .append('\n');
                n++;
            }
        } catch (Throwable t) {
            sb.append("EXCEPTION: ").append(describe(t)).append('\n');
        } finally {
            if (c != null) c.close();
        }
    }

    // ------------------------------------------------------------------ 工具

    private static void appendProps(StringBuilder sb, String dump, String[] keys) {
        String[] lines = dump == null ? new String[0] : dump.split("\n");
        for (String k : keys) {
            String val = "(not set)";
            String needle = "[" + k + "]";
            for (String line : lines) {
                if (line.startsWith(needle)) {
                    int i = line.indexOf("]:");
                    val = i >= 0 ? line.substring(i + 2).trim() : line;
                    break;
                }
            }
            sb.append(String.format(Locale.US, "%-32s = %s", k, val)).append('\n');
        }
    }

    private static String describe(Throwable t) {
        String msg = t.getMessage();
        return t.getClass().getName() + (msg == null ? "" : (": " + msg));
    }

    private static String sh(String cmd) {
        Process p = null;
        try {
            p = Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd});
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append('\n');
            }
            p.waitFor();
            return sb.toString();
        } catch (Throwable t) {
            return "ERR " + describe(t);
        } finally {
            if (p != null) p.destroy();
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}