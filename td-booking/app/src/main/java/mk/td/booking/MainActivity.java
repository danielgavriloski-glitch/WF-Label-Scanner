package mk.td.booking;

import android.app.Activity;
import android.os.Bundle;
import android.content.Context;
import android.print.PrintManager;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import android.app.AlertDialog;
import android.Manifest;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Events;
import org.json.JSONObject;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;

public class MainActivity extends Activity {
    private WebView web;
    private String pendingWedding;
    private static final int CALENDAR_PERMISSION = 42;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        web = new WebView(this);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(true);
        web.getSettings().setDefaultTextEncodingName("UTF-8");
        web.addJavascriptInterface(new Bridge(), "TDNative");
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                new AlertDialog.Builder(MainActivity.this).setMessage(message).setPositiveButton("Во ред", (d, w) -> result.confirm()).setOnCancelListener(d -> result.confirm()).show();
                return true;
            }
            @Override public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
                new AlertDialog.Builder(MainActivity.this).setMessage(message).setPositiveButton("Да", (d, w) -> result.confirm()).setNegativeButton("Не", (d, w) -> result.cancel()).setOnCancelListener(d -> result.cancel()).show();
                return true;
            }
        });
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
    }
    public class Bridge {
        @JavascriptInterface public void printContract() {
            runOnUiThread(() -> {
                PrintManager manager = (PrintManager)getSystemService(Context.PRINT_SERVICE);
                manager.print("TD Booking договор", web.createPrintDocumentAdapter("TD Booking договор"), null);
            });
        }
        @JavascriptInterface public void syncWedding(String json) {
            runOnUiThread(() -> syncWeddingOnUi(json));
        }
    }
    private void syncWeddingOnUi(String json) {
        pendingWedding = json;
        if (checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR}, CALENDAR_PERMISSION);
            return;
        }
        try {
            JSONObject wedding = new JSONObject(json);
            if (wedding.has("calendarId") && wedding.optLong("calendarId", -1) > 0) {
                writeEvent(wedding, wedding.getLong("calendarId"));
                return;
            }
            ArrayList<Long> ids = new ArrayList<>();
            ArrayList<String> names = new ArrayList<>();
            try (Cursor cursor = getContentResolver().query(Calendars.CONTENT_URI,
                    new String[]{Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.ACCOUNT_NAME},
                    Calendars.ACCOUNT_TYPE + "=? AND " + Calendars.CALENDAR_ACCESS_LEVEL + ">=?",
                    new String[]{"com.google", String.valueOf(Calendars.CAL_ACCESS_CONTRIBUTOR)}, null)) {
                if (cursor != null) while (cursor.moveToNext()) {
                    ids.add(cursor.getLong(0));
                    names.add(cursor.getString(1) + " (" + cursor.getString(2) + ")");
                }
            }
            if (ids.isEmpty()) { showMessage("Нема достапен Google календар. Провери дали Google Calendar е додаден на телефонот и синхронизацијата е вклучена."); return; }
            new AlertDialog.Builder(this).setTitle("Избери Google Calendar")
                    .setItems(names.toArray(new String[0]), (d, which) -> writeEvent(wedding, ids.get(which)))
                    .setNegativeButton("Откажи", null).show();
        } catch (Exception e) { showMessage("Не можам да го поврзам календарот: " + e.getMessage()); }
    }
    private void writeEvent(JSONObject wedding, long calendarId) {
        try {
            LocalDate date = LocalDate.parse(wedding.getString("date"));
            String t = wedding.optString("start", "");
            LocalTime time = t.isEmpty() ? LocalTime.of(10, 0) : LocalTime.parse(t);
            long begin = ZonedDateTime.of(date, time, ZoneId.of("Europe/Skopje")).toInstant().toEpochMilli();
            long end = begin + 12L * 60L * 60L * 1000L;
            ContentValues values = new ContentValues();
            values.put(Events.CALENDAR_ID, calendarId);
            values.put(Events.TITLE, "TD Production · " + wedding.optString("bride") + " и " + wedding.optString("groom"));
            values.put(Events.DTSTART, begin);
            values.put(Events.DTEND, end);
            values.put(Events.EVENT_TIMEZONE, "Europe/Skopje");
            values.put(Events.EVENT_LOCATION, wedding.optString("venue"));
            values.put(Events.DESCRIPTION, "Пакет: " + wedding.optString("services") + "\nTD Booking резервација");
            long eventId = wedding.optLong("calendarEventId", -1);
            int updated = 0;
            if (eventId > 0) updated = getContentResolver().update(Uri.withAppendedPath(Events.CONTENT_URI, String.valueOf(eventId)), values, null, null);
            if (updated == 0) {
                Uri inserted = getContentResolver().insert(Events.CONTENT_URI, values);
                if (inserted == null) throw new IllegalStateException("Календарот не го зачува настанот");
                eventId = Long.parseLong(inserted.getLastPathSegment());
            }
            String id = JSONObject.quote(wedding.getString("id"));
            web.evaluateJavascript("onCalendarSynced(" + id + "," + eventId + "," + calendarId + ")", null);
        } catch (Exception e) { showMessage("Настанот не е зачуван: " + e.getMessage()); }
    }
    private void showMessage(String message) {
        new AlertDialog.Builder(this).setMessage(message).setPositiveButton("Во ред", null).show();
    }
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(requestCode, permissions, grants);
        if (requestCode == CALENDAR_PERMISSION && grants.length == 2 && grants[0] == PackageManager.PERMISSION_GRANTED && grants[1] == PackageManager.PERMISSION_GRANTED) {
            syncWeddingOnUi(pendingWedding);
        } else if (requestCode == CALENDAR_PERMISSION) showMessage("Дозволи пристап до календарот за синхронизација.");
    }
    @Override public void onBackPressed() {
        if(web.canGoBack()) web.goBack(); else super.onBackPressed();
    }
}
