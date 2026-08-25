package titus.vietclockwidget;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class VietnameseClockWidget extends AppWidgetProvider {
    static final String ACTION_REFRESH = "titus.vietclockwidget.ACTION_REFRESH";
    static final String ACTION_MINUTE = "titus.vietclockwidget.ACTION_MINUTE";
    static final String ACTION_ANIMATION = "titus.vietclockwidget.ACTION_ANIMATION";
    private static final String PREFS = "widget_weather";
    private static final String KEY_READY = "ready";
    private static final String KEY_CITY = "city";
    private static final String KEY_LOW = "low";
    private static final String KEY_HIGH = "high";
    private static final String KEY_TEMP = "temp";
    private static final String KEY_CONDITION = "condition";
    private static final String KEY_ICON = "icon";
    private static final String KEY_ALERT = "alert";
    private static final String KEY_UPDATED = "updated";
    private static final long MINUTE = 60_000L;
    private static final long ANIMATION_TICK = 10_000L;
    private static final Pattern NUMBER_PATTERN = Pattern.compile("[-+]?\\d+(?:[.,]\\d+)?");
    private static final String[] COLOROS_WEATHER_AUTHORITIES = {
            "com.coloros.weather.service.provider.data",
            "com.oplus.weather.service.provider.data",
            "com.oplusos.weather.service.provider.data"
    };
    private static final String[] WEATHER_INFO_PATHS = {"oplus_weather_info", "weather_info"};
    private static final String[] CITY_PATHS = {"attent_city", "resident_city"};
    private static final String[] ALERT_PATHS = {"weather_warn", "weather_warning"};
    private static final String[] LOCATION_CALLBACK_URIS = {
            "content://com.oplus.weather.provider.locationCallBack",
            "content://com.coloros.weather.provider.locationCallBack"
    };
    private static final String[] WEATHER_UPDATE_ACTIONS = {
            "com.oplus.weather.action.update_weather",
            "com.oplus.weather.action.updatecomplete",
            "com.oplus.weather.action.deal_location",
            "com.oplus.weatherwidget.WEATHER_UPDATE",
            "com.oppo.action.oppoWeather"
    };
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
        if (ACTION_MINUTE.equals(action) || ACTION_ANIMATION.equals(action)) {
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
        if (isSystemWeatherAction(action)) {
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
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        boolean ready = prefs.getBoolean(KEY_READY, false);
        String city = prefs.getString(KEY_CITY, "Vị trí hiện tại");
        String temperature = prefs.getString(KEY_TEMP, "");
        String condition = prefs.getString(KEY_CONDITION, "Đang cập nhật");
        String icon = prefs.getString(KEY_ICON, "sun");
        String alert = prefs.getString(KEY_ALERT, "");
        views.setImageViewBitmap(R.id.iv_clock_canvas,
                ClockCanvasRenderer.render(context, now, city, temperature,
                        condition, icon, alert, ready));

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
        Location location = preciseLocation(context);
        WeatherData systemWeather = fetchColorOsWeather(context, location);
        // The ColorOS provider is the source of truth: it follows the phone's
        // selected location, district-level weather and system warnings.
        if (systemWeather.ready) {
            return systemWeather;
        }
        // Keep a coordinate fallback only for devices that deny access to the
        // private ColorOS provider; it is never preferred over system data.
        return fetchWeatherFromNetwork(context, location);
    }

    private static WeatherData fetchWeatherFromNetwork(Context context, Location location) {
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
                    "",
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
        editor.putString(KEY_ALERT, weather.alert);
        editor.putLong(KEY_UPDATED, System.currentTimeMillis());
        editor.apply();
    }

    /**
     * Reads the same data source used by ColorOS Weather. The provider is
     * intentionally tried before the network fallback so the widget follows
     * the phone's selected location, language, icon data and warning feed.
     */
    private static WeatherData fetchColorOsWeather(Context context, Location location) {
        ContentResolver resolver = context.getContentResolver();
        for (String authority : COLOROS_WEATHER_AUTHORITIES) {
            for (String path : WEATHER_INFO_PATHS) {
                Cursor cursor = null;
                try {
                    cursor = resolver.query(
                            Uri.parse("content://" + authority + "/" + path),
                            null,
                            null,
                            null,
                            null
                    );
                    if (cursor == null) {
                        continue;
                    }

                    WeatherRow row = firstWeatherRow(cursor);
                    if (row == null || !row.ready) {
                        continue;
                    }

                    String city = locationName(context, location);
                    if (isFallbackLocationName(city)) {
                        city = firstNonEmpty(
                                findCallbackCity(resolver),
                                findSystemCity(resolver, authority, row.cityId)
                        );
                    }
                    String alert = firstNonEmpty(row.alert, findSystemAlert(resolver, authority, row.cityId));
                    return new WeatherData(
                            TextUtils.isEmpty(city) ? "Vị trí hiện tại" : city,
                            row.low,
                            row.high,
                            row.temperature,
                            row.condition,
                            row.icon,
                            alert,
                            true
                    );
                } catch (SecurityException | IllegalArgumentException ignored) {
                    // Some ColorOS builds keep the provider private. Try the
                    // other provider name/path, then use the normal fallback.
                } finally {
                    if (cursor != null) {
                        cursor.close();
                    }
                }
            }
        }
        return WeatherData.unavailable();
    }

    private static WeatherRow firstWeatherRow(Cursor cursor) {
        WeatherRow candidate = null;
        while (cursor.moveToNext()) {
            long cityId = longValue(cursor, "city_id", -1L);
            String temperature = temperatureValue(firstNonEmpty(
                    stringValue(cursor, "current_temp"),
                    stringValue(cursor, "real_feel_temp"),
                    stringValue(cursor, "day_temp"),
                    stringValue(cursor, "night_temp")
            ));
            String currentWeather = firstNonEmpty(
                    stringValue(cursor, "current_weather"),
                    stringValue(cursor, "day_weather"),
                    stringValue(cursor, "night_weather")
            );
            String weatherCode = firstNonEmpty(
                    stringValue(cursor, "weather_id"),
                    stringValue(cursor, "day_weather_id")
            );
            String condition = systemCondition(currentWeather, weatherCode);
            String icon = systemIcon(currentWeather, weatherCode);
            String low = temperatureValue(firstNonEmpty(
                    stringValue(cursor, "night_temp"),
                    stringValue(cursor, "day_temp"),
                    temperature
            ));
            String high = temperatureValue(firstNonEmpty(
                    stringValue(cursor, "day_temp"),
                    stringValue(cursor, "night_temp"),
                    temperature
            ));
            String alert = cleanAlert(firstNonEmpty(
                    stringValue(cursor, "warn_weather"),
                    stringValue(cursor, "detail_warn_weather")
            ));
            boolean current = intValue(cursor, "current", 0) == 1
                    || intValue(cursor, "location", 0) == 1;
            WeatherRow row = new WeatherRow(
                    cityId,
                    low,
                    high,
                    temperature,
                    condition,
                    icon,
                    alert,
                    !TextUtils.isEmpty(temperature) || !TextUtils.isEmpty(currentWeather)
            );
            if (current) {
                return row;
            }
            if (candidate == null && row.ready) {
                candidate = row;
            }
        }
        return candidate;
    }

    private static String findSystemCity(ContentResolver resolver, String authority, long cityId) {
        for (String path : CITY_PATHS) {
            Cursor cursor = null;
            try {
                cursor = resolver.query(
                        Uri.parse("content://" + authority + "/" + path),
                        null,
                        null,
                        null,
                        null
                );
                if (cursor == null) {
                    continue;
                }
                String fallback = "";
                while (cursor.moveToNext()) {
                    String name = firstNonEmpty(
                            stringValue(cursor, "city_name"),
                            stringValue(cursor, "placeName"),
                            stringValue(cursor, "full_address")
                    );
                    if (TextUtils.isEmpty(name)) {
                        continue;
                    }
                    if (TextUtils.isEmpty(fallback)) {
                        fallback = name;
                    }
                    long rowCityId = longValue(cursor, "city_id", -1L);
                    boolean current = intValue(cursor, "current", 0) == 1
                            || intValue(cursor, "location", 0) == 1;
                    if ((cityId >= 0 && rowCityId == cityId) || current) {
                        return cleanLocationName(name);
                    }
                }
                if (!TextUtils.isEmpty(fallback)) {
                    return cleanLocationName(fallback);
                }
            } catch (SecurityException | IllegalArgumentException ignored) {
                // Try the next provider alias/path.
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        return "";
    }

    private static String findCallbackCity(ContentResolver resolver) {
        for (String callbackUri : LOCATION_CALLBACK_URIS) {
            Cursor cursor = null;
            try {
                cursor = resolver.query(Uri.parse(callbackUri), null, null, null, null);
                if (cursor == null) {
                    continue;
                }
                while (cursor.moveToNext()) {
                    String name = firstNonEmpty(
                            stringValue(cursor, "district_name"),
                            stringValue(cursor, "sub_locality"),
                            stringValue(cursor, "county"),
                            stringValue(cursor, "city_name"),
                            stringValue(cursor, "placeName"),
                            stringValue(cursor, "locality")
                    );
                    if (!TextUtils.isEmpty(name)) {
                        return cleanLocationName(name);
                    }
                }
            } catch (SecurityException | IllegalArgumentException ignored) {
                // The callback provider is private on some ColorOS releases.
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        return "";
    }

    private static String findSystemAlert(ContentResolver resolver, String authority, long cityId) {
        for (String path : ALERT_PATHS) {
            Cursor cursor = null;
            try {
                cursor = resolver.query(
                        Uri.parse("content://" + authority + "/" + path),
                        null,
                        null,
                        null,
                        null
                );
                if (cursor == null) {
                    continue;
                }
                while (cursor.moveToNext()) {
                    long rowCityId = longValue(cursor, "city_id", longValue(cursor, "attent_city_id", -1L));
                    if (cityId >= 0 && rowCityId >= 0 && rowCityId != cityId) {
                        continue;
                    }
                    String alert = cleanAlert(firstNonEmpty(
                            stringValue(cursor, "warn_title"),
                            stringValue(cursor, "warn_content"),
                            stringValue(cursor, "warn_weather"),
                            stringValue(cursor, "detail_warn_weather"),
                            stringValue(cursor, "content"),
                            stringValue(cursor, "title")
                    ));
                    if (!TextUtils.isEmpty(alert)) {
                        return alert;
                    }
                }
            } catch (SecurityException | IllegalArgumentException ignored) {
                // The warning table is optional on some ColorOS releases.
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        return "";
    }

    private static String stringValue(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        if (index < 0 || cursor.isNull(index)) {
            return "";
        }
        return cursor.getString(index);
    }

    private static long longValue(Cursor cursor, String column, long fallback) {
        String value = stringValue(cursor, column);
        if (TextUtils.isEmpty(value)) {
            return fallback;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int intValue(Cursor cursor, String column, int fallback) {
        return (int) longValue(cursor, column, fallback);
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (!TextUtils.isEmpty(value) && !"null".equalsIgnoreCase(value)
                    && !"[]".equals(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private static String temperatureValue(String value) {
        if (TextUtils.isEmpty(value)) {
            return "";
        }
        Matcher matcher = NUMBER_PATTERN.matcher(value.replace(',', '.'));
        if (!matcher.find()) {
            return "";
        }
        try {
            return String.valueOf(Math.round(Double.parseDouble(matcher.group())));
        } catch (NumberFormatException ignored) {
            return "";
        }
    }

    private static String systemCondition(String description, String code) {
        String value = description == null ? "" : description.trim();
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.contains("storm") || lower.contains("thunder") || lower.contains("dông")
                || lower.contains("giông")) {
            return "Dông";
        }
        if (lower.contains("rain") || lower.contains("mưa") || lower.contains("drizzle")) {
            return "Mưa";
        }
        if (lower.contains("fog") || lower.contains("sương")) {
            return "Sương mù";
        }
        if (lower.contains("cloud") || lower.contains("mây")) {
            return "Ít mây";
        }
        if (lower.contains("clear") || lower.contains("sun") || lower.contains("quang")) {
            return "Trời quang";
        }
        int weatherCode = parseInt(code, -1);
        return weatherCode >= 0 ? condition(weatherCode) :
                (TextUtils.isEmpty(value) ? "Đang cập nhật" : value);
    }

    private static String systemIcon(String description, String code) {
        String lower = description == null ? "" : description.toLowerCase(Locale.ROOT);
        if (lower.contains("storm") || lower.contains("thunder") || lower.contains("dông")
                || lower.contains("giông")) {
            return "storm";
        }
        if (lower.contains("rain") || lower.contains("mưa") || lower.contains("drizzle")) {
            return "rain";
        }
        if (lower.contains("cloud") || lower.contains("mây") || lower.contains("fog")
                || lower.contains("sương")) {
            return "cloud";
        }
        int weatherCode = parseInt(code, -1);
        return weatherCode >= 0 ? weatherIcon(weatherCode) : "sun";
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String cleanAlert(String value) {
        if (TextUtils.isEmpty(value) || "0".equals(value) || "[]".equals(value)
                || "{}".equals(value)) {
            return "";
        }
        String cleaned = value.replaceAll("\\s+", " ").trim();
        if (cleaned.length() > 32) {
            cleaned = cleaned.substring(0, 31).trim() + "…";
        }
        return cleaned;
    }

    private static boolean isSystemWeatherAction(String action) {
        if (action == null) {
            return false;
        }
        for (String weatherAction : WEATHER_UPDATE_ACTIONS) {
            if (weatherAction.equals(action)) {
                return true;
            }
        }
        return false;
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

    private static Location preciseLocation(Context context) {
        Location last = lastKnownLocation(context);
        Location current = requestCurrentLocation(context);
        if (current == null) {
            return last;
        }
        if (last == null || current.getAccuracy() <= last.getAccuracy()
                || current.getTime() >= last.getTime()) {
            return current;
        }
        return last;
    }

    private static Location requestCurrentLocation(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return null;
        }
        boolean coarse = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean fine = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        if (!coarse && !fine) {
            return null;
        }

        LocationManager manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        CountDownLatch latch = new CountDownLatch(1);
        final Location[] result = new Location[1];
        String[] providers = {LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER};
        for (String provider : providers) {
            try {
                if (!manager.isProviderEnabled(provider)) {
                    continue;
                }
                manager.getCurrentLocation(
                        provider,
                        null,
                        context.getMainExecutor(),
                        location -> {
                            if (location != null && result[0] == null) {
                                result[0] = location;
                                latch.countDown();
                            }
                        }
                );
            } catch (SecurityException | IllegalArgumentException ignored) {
                // Try the other provider, then use the last known fix.
            }
        }
        try {
            latch.await(2500L, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        return result[0];
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

    private static boolean isFallbackLocationName(String name) {
        return TextUtils.isEmpty(name) || "Vị trí hiện tại".equals(name);
    }

    private static WeatherData withAlert(WeatherData weather, String alert) {
        return new WeatherData(
                weather.city,
                weather.low,
                weather.high,
                weather.temperature,
                weather.condition,
                weather.icon,
                alert,
                weather.ready
        );
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
        if (code == 0) return "sun";
        if (code == 1 || code == 2 || code == 3) return "cloud";
        if (code >= 51 && code <= 82) return "rain";
        if (code >= 95) return "storm";
        return "cloud";
    }

    private static int weatherIconResource(String icon) {
        if ("rain".equals(icon)) return R.drawable.weather_rain;
        if ("storm".equals(icon)) return R.drawable.weather_storm;
        if ("cloud".equals(icon)) return R.drawable.weather_cloud;
        return R.drawable.weather_sun;
    }

    private static int digitResource(char digit, boolean redOne) {
        if (redOne && digit == '1') return R.drawable.digit_1_red;
        switch (digit) {
            case '0': return R.drawable.digit_0_white;
            case '1': return R.drawable.digit_1_white;
            case '2': return R.drawable.digit_2_white;
            case '3': return R.drawable.digit_3_white;
            case '4': return R.drawable.digit_4_white;
            case '5': return R.drawable.digit_5_white;
            case '6': return R.drawable.digit_6_white;
            case '7': return R.drawable.digit_7_white;
            case '8': return R.drawable.digit_8_white;
            default: return R.drawable.digit_9_white;
        }
    }

    private static void scheduleMinuteUpdates(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pendingIntent = minutePendingIntent(context);
        long now = System.currentTimeMillis();
        long nextMinute = now - (now % MINUTE) + MINUTE;
        alarmManager.setInexactRepeating(AlarmManager.RTC, nextMinute, MINUTE, pendingIntent);
        PendingIntent animationIntent = animationPendingIntent(context);
        alarmManager.setInexactRepeating(
                AlarmManager.RTC,
                now + ANIMATION_TICK,
                ANIMATION_TICK,
                animationIntent
        );
    }

    private static void cancelMinuteUpdates(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(minutePendingIntent(context));
        alarmManager.cancel(animationPendingIntent(context));
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

    private static PendingIntent animationPendingIntent(Context context) {
        Intent intent = new Intent(context, VietnameseClockWidget.class).setAction(ACTION_ANIMATION);
        return PendingIntent.getBroadcast(
                context,
                78,
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
        final String alert;
        final boolean ready;

        WeatherData(String city, String low, String high, String temperature,
                    String condition, String icon, String alert, boolean ready) {
            this.city = city;
            this.low = low;
            this.high = high;
            this.temperature = temperature;
            this.condition = condition;
            this.icon = icon;
            this.alert = alert;
            this.ready = ready;
        }

        static WeatherData unavailable() {
            return new WeatherData(
                    "Vị trí hiện tại",
                    "—",
                    "—",
                    "—",
                    "Chưa có dữ liệu",
                    "sun",
                    "",
                    false
            );
        }
    }

    private static final class WeatherRow {
        final long cityId;
        final String low;
        final String high;
        final String temperature;
        final String condition;
        final String icon;
        final String alert;
        final boolean ready;

        WeatherRow(long cityId, String low, String high, String temperature,
                   String condition, String icon, String alert, boolean ready) {
            this.cityId = cityId;
            this.low = TextUtils.isEmpty(low) ? "—" : low;
            this.high = TextUtils.isEmpty(high) ? "—" : high;
            this.temperature = TextUtils.isEmpty(temperature) ? "—" : temperature;
            this.condition = TextUtils.isEmpty(condition) ? "Đang cập nhật" : condition;
            this.icon = TextUtils.isEmpty(icon) ? "sun" : icon;
            this.alert = alert;
            this.ready = ready;
        }
    }
}
