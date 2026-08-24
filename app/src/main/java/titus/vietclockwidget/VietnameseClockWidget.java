package titus.vietclockwidget;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VietnameseClockWidget extends AppWidgetProvider {
    static final String ACTION_REFRESH = "titus.vietclockwidget.ACTION_REFRESH";
    static final String ACTION_MINUTE = "titus.vietclockwidget.ACTION_MINUTE";
    private static final String PREFS = "widget_weather";
    private static final String KEY_READY = "ready";
    private static final String KEY_CITY = "city";
    private static final String KEY_LOW = "low";
    private static final String KEY_HIGH = "high";
    private static final String KEY_TEMP = "temp";
    private static final String KEY_CONDITION = "condition";
    private static final String KEY_ICON = "icon";
    private static final String KEY_UPDATED = "updated";
    private static final long MINUTE = 60_000L;
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();

    @Override
    public void onEnabled(Context context) {
        scheduleMinuteUpdates(context);
    }

    @Override
    public void onDisabled(Context context) {
        cancelMinuteUpdates(context);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        scheduleMinuteUpdates(context);
        for (int appWidgetId : appWidgetIds) {
            updateView(context, manager, appWidgetId);
        }
        refreshWeatherAsync(context, appWidgetIds);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_MINUTE.equals(action)) {
            updateAllCached(context);
            return;
        }
        if (ACTION_REFRESH.equals(action)
                || Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                || Intent.ACTION_DATE_CHANGED.equals(action)) {
            updateAllCached(context);
            refreshWeatherAsync(context, getWidgetIds(context));
            return;
        }
        super.onReceive(context, intent);
    }

    static void requestRefresh(Context context) {
        Intent intent = new Intent(context, VietnameseClockWidget.class)
                .setAction(ACTION_REFRESH);
        context.sendBroadcast(intent);
    }

    private void refreshWeatherAsync(Context context, int[] ids) {
        if (ids == null || ids.length == 0) {
            return;
        }
        final Context appContext = context.getApplicationContext();
        final PendingResult pendingResult = goAsync();
        WORKER.execute(() -> {
            try {
                WeatherData weather = fetchWeather(appContext);
                saveWeather(appContext, weather);
                updateAllCached(appContext);
            } finally {
                pendingResult.finish();
            }
        });
    }

    private static void updateAllCached(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = getWidgetIds(context);
        for (int id : ids) {
            updateView(context, manager, id);
        }
    }

    private static int[] getWidgetIds(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName provider = new ComponentName(context, VietnameseClockWidget.class);
        return manager.getAppWidgetIds(provider);
    }

    private static void updateView(Context context, AppWidgetManager manager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_clock);
        Calendar now = Calendar.getInstance();
        String hour = twoDigits(now.get(Calendar.HOUR_OF_DAY));
        String minute = twoDigits(now.get(Calendar.MINUTE));

        views.setTextViewText(R.id.tv_hour_tens, hour.substring(0, 1));
        views.setTextViewText(R.id.tv_hour_ones, hour.substring(1, 2));
        int white = context.getResources().getColor(R.color.widget_text);
        int red = context.getResources().getColor(R.color.widget_red);
        views.setTextColor(R.id.tv_hour_tens, hour.charAt(0) == '1' ? red : white);
        views.setTextColor(R.id.tv_hour_ones, hour.charAt(1) == '1' ? red : white);
        views.setTextViewText(R.id.tv_minute, minute);
        views.setTextViewText(R.id.tv_weekday, weekday(now.get(Calendar.DAY_OF_WEEK)));
        views.setTextViewText(R.id.tv_month, "Tháng " + (now.get(Calendar.MONTH) + 1));
        views.setTextViewText(R.id.tv_solar_date, String.format(
                Locale.US,
                "%04d-%02d-%02d",
                now.get(Calendar.YEAR),
                now.get(Calendar.MONTH) + 1,
                now.get(Calendar.DAY_OF_MONTH)
        ));
        views.setTextViewText(R.id.tv_lunar_date, LunarCalendar.shortLabel(now));

        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        boolean ready = prefs.getBoolean(KEY_READY, false);
        if (ready) {
            views.setTextViewText(R.id.tv_low_temp, "▼ " + prefs.getString(KEY_LOW, "—") + "°");
            views.setTextViewText(R.id.tv_high_temp, "▲ " + prefs.getString(KEY_HIGH, "—") + "°");
            views.setTextViewText(R.id.tv_location, "⌖ " + prefs.getString(KEY_CITY, "Vị trí hiện tại"));
            views.setTextViewText(R.id.tv_condition, prefs.getString(KEY_CONDITION, "Đang cập nhật"));
            views.setTextViewText(R.id.tv_weather_icon, prefs.getString(KEY_ICON, "☀"));
        } else {
            views.setTextViewText(R.id.tv_low_temp, "▼ —°");
            views.setTextViewText(R.id.tv_high_temp, "▲ —°");
            views.setTextViewText(R.id.tv_location, "⌖ Bật vị trí");
            views.setTextViewText(R.id.tv_condition, "Bật vị trí để cập nhật");
            views.setTextViewText(R.id.tv_weather_icon, "☀");
        }

        Intent openApp = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPendingIntent = PendingIntent.getActivity(
                context,
                1000 + appWidgetId,
                openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        Intent refresh = new Intent(context, VietnameseClockWidget.class)
                .setAction(ACTION_REFRESH)
                .setData(Uri.parse("vietclock://refresh/" + appWidgetId));
        PendingIntent refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                2000 + appWidgetId,
                refresh,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        views.setOnClickPendingIntent(R.id.widget_root, openPendingIntent);
        views.setOnClickPendingIntent(R.id.weather_block, refreshPendingIntent);
        manager.updateAppWidget(appWidgetId, views);
    }

    private static WeatherData fetchWeather(Context context) {
        Location location = lastKnownLocation(context);
        if (location == null) {
            return WeatherData.unavailable();
        }

        String city = locationName(context, location);
        HttpURLConnection connection = null;
        try {
            String endpoint = "https://api.open-meteo.com/v1/forecast"
                    + "?latitude=" + location.getLatitude()
                    + "&longitude=" + location.getLongitude()
                    + "&current=temperature_2m,weather_code"
                    + "&daily=temperature_2m_min,temperature_2m_max"
                    + "&forecast_days=1&timezone=auto";
            connection = (HttpURLConnection) new URL(endpoint).openConnection();
            connection.setConnectTimeout(6000);
            connection.setReadTimeout(6000);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");

            int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                return WeatherData.unavailable();
            }
            String body = readAll(connection.getInputStream());
            JSONObject root = new JSONObject(body);
            JSONObject current = root.optJSONObject("current");
            JSONObject daily = root.optJSONObject("daily");
            if (current == null || daily == null) {
                return WeatherData.unavailable();
            }

            double temperature = current.optDouble("temperature_2m", Double.NaN);
            int code = current.optInt("weather_code", -1);
            JSONArray mins = daily.optJSONArray("temperature_2m_min");
            JSONArray maxs = daily.optJSONArray("temperature_2m_max");
            double low = mins == null ? temperature : mins.optDouble(0, temperature);
            double high = maxs == null ? temperature : maxs.optDouble(0, temperature);
            if (Double.isNaN(temperature)) {
                return WeatherData.unavailable();
            }
            return new WeatherData(
                    city,
                    rounded(low),
                    rounded(high),
                    rounded(temperature),
                    condition(code),
                    weatherIcon(code),
                    true
            );
        } catch (Exception ignored) {
            return WeatherData.unavailable();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static void saveWeather(Context context, WeatherData weather) {
        if (!weather.ready) {
            return;
        }
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        editor.putBoolean(KEY_READY, weather.ready);
        editor.putString(KEY_CITY, weather.city);
        editor.putString(KEY_LOW, weather.low);
        editor.putString(KEY_HIGH, weather.high);
        editor.putString(KEY_TEMP, weather.temperature);
        editor.putString(KEY_CONDITION, weather.condition);
        editor.putString(KEY_ICON, weather.icon);
        editor.putLong(KEY_UPDATED, System.currentTimeMillis());
        editor.apply();
    }

    private static Location lastKnownLocation(Context context) {
        boolean coarse = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean fine = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        if (!coarse && !fine) {
            return null;
        }

        LocationManager manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        Location best = null;
        String[] providers = {LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER};
        for (String provider : providers) {
            try {
                Location candidate = manager.getLastKnownLocation(provider);
                if (candidate != null && (best == null || candidate.getTime() > best.getTime())) {
                    best = candidate;
                }
            } catch (SecurityException ignored) {
                // Permission can change while the widget is updating.
            }
        }
        return best;
    }

    private static String locationName(Context context, Location location) {
        String fallback = "Vị trí hiện tại";
        if (!Geocoder.isPresent()) {
            return fallback;
        }
        try {
            Geocoder geocoder = new Geocoder(context, new Locale("vi", "VN"));
            List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
            if (addresses == null || addresses.isEmpty()) {
                return fallback;
            }
            Address address = addresses.get(0);
            String name = address.getSubLocality();
            if (name == null || name.trim().isEmpty()) {
                name = address.getLocality();
            }
            if (name == null || name.trim().isEmpty()) {
                name = address.getSubAdminArea();
            }
            return cleanLocationName(name == null ? fallback : name);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String cleanLocationName(String name) {
        String value = name.trim();
        String[] prefixes = {"Quận ", "Huyện ", "Thành phố ", "Tỉnh ", "Thị xã "};
        for (String prefix : prefixes) {
            if (value.startsWith(prefix)) {
                value = value.substring(prefix.length());
                break;
            }
        }
        return value.isEmpty() ? "Vị trí hiện tại" : value;
    }

    private static String readAll(InputStream inputStream) throws Exception {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }
        return builder.toString();
    }

    private static String weekday(int dayOfWeek) {
        String[] days = {"", "Chủ nhật", "Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy"};
        return dayOfWeek >= 1 && dayOfWeek <= 7 ? days[dayOfWeek] : "";
    }

    private static String twoDigits(int value) {
        return value < 10 ? "0" + value : String.valueOf(value);
    }

    private static String rounded(double value) {
        if (Double.isNaN(value)) {
            return "—";
        }
        return String.valueOf(Math.round(value));
    }

    private static String condition(int code) {
        if (code == 0) return "Trời quang";
        if (code == 1 || code == 2 || code == 3) return "Ít mây";
        if (code == 45 || code == 48) return "Sương mù";
        if (code >= 51 && code <= 57) return "Mưa phùn";
        if (code == 61 || code == 63 || code == 65 || code == 80 || code == 81) return "Mưa";
        if (code == 66 || code == 67 || code == 82) return "Mưa rào";
        if (code >= 71 && code <= 77) return "Tuyết";
        if (code == 95 || code == 96 || code == 99) return "Dông";
        return "Nhiều mây";
    }

    private static String weatherIcon(int code) {
        if (code == 0) return "☀";
        if (code == 1 || code == 2 || code == 3) return "☁";
        if (code >= 51 && code <= 82) return "☂";
        if (code >= 95) return "⚡";
        return "☁";
    }

    private static void scheduleMinuteUpdates(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pendingIntent = minutePendingIntent(context);
        long now = System.currentTimeMillis();
        long nextMinute = now - (now % MINUTE) + MINUTE;
        alarmManager.setInexactRepeating(AlarmManager.RTC, nextMinute, MINUTE, pendingIntent);
    }

    private static void cancelMinuteUpdates(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(minutePendingIntent(context));
    }

    private static PendingIntent minutePendingIntent(Context context) {
        Intent intent = new Intent(context, VietnameseClockWidget.class).setAction(ACTION_MINUTE);
        return PendingIntent.getBroadcast(
                context,
                77,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static final class WeatherData {
        final String city;
        final String low;
        final String high;
        final String temperature;
        final String condition;
        final String icon;
        final boolean ready;

        WeatherData(String city, String low, String high, String temperature,
                    String condition, String icon, boolean ready) {
            this.city = city;
            this.low = low;
            this.high = high;
            this.temperature = temperature;
            this.condition = condition;
            this.icon = icon;
            this.ready = ready;
        }

        static WeatherData unavailable() {
            return new WeatherData("Vị trí hiện tại", "—", "—", "—", "Chưa có dữ liệu", "⟳", false);
        }
    }
}
