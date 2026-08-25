package titus.vietclockwidget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.text.TextUtils;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Renders the original MAML artwork into one bitmap for a normal Android
 * AppWidget. The source PNGs are kept at their original dimensions and the
 * coordinates mirror manifest.xml's 1080px canvas.
 */
final class ClockCanvasRenderer {
    private static final int WIDTH = 1080;
    private static final int HEIGHT = 540;
    private static final Map<Integer, Bitmap> CACHE = new HashMap<>();
    private static final String[] WEEKDAY = {
            "", "Chủ nhật", "Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy"
    };

    private ClockCanvasRenderer() {
    }

    static Bitmap render(Context context, Calendar now, String city, String temperature,
                         String condition, String icon, String alert, boolean ready) {
        Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        paint.setDither(true);

        // Four original digit PNGs plus the original narrow divider asset.
        String hour = twoDigits(now.get(Calendar.HOUR_OF_DAY));
        String minute = twoDigits(now.get(Calendar.MINUTE));
        drawAsset(context, canvas, resource(context, "clock_time_" + hour.charAt(0)), 339, 40, paint);
        drawAsset(context, canvas, resource(context, "clock_time_" + hour.charAt(1)), 418, 40, paint);
        drawAsset(context, canvas, resource(context, "clock_time_dot"), 497, 40, paint);
        drawAsset(context, canvas, resource(context, "clock_time_" + minute.charAt(0)), 576, 40, paint);
        drawAsset(context, canvas, resource(context, "clock_time_" + minute.charAt(1)), 655, 40, paint);

        drawAsset(context, canvas,
                resource(context, "clock_week_" + now.get(Calendar.DAY_OF_WEEK)),
                330, 190, paint);
        drawAsset(context, canvas,
                resource(context, "clock_date_" + now.get(Calendar.DAY_OF_MONTH)),
                613, 190, paint);
        drawAsset(context, canvas,
                resource(context, "clock_month_" + (now.get(Calendar.MONTH) + 1)),
                693, 190, paint);

        paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(30f);
        paint.setColor(Color.WHITE);
        String lunar = LunarCalendar.shortLabel(now);
        canvas.drawText(lunar, 540f, 275f, paint);

        int weatherId = sourceWeatherId(icon);
        int weatherRes = resource(context, "clock_weather_" + weatherId);
        if (weatherRes == 0) {
            weatherRes = resource(context, "clock_weather_weather");
        }
        drawCentered(context, canvas, weatherRes, 535, 305, paint);

        String safeCity = TextUtils.isEmpty(city) ? "Vị trí hiện tại" : city;
        if (!ready) {
            safeCity = "Bật vị trí";
            temperature = "";
            condition = "Bật vị trí để cập nhật";
        }
        if (!TextUtils.isEmpty(alert)) {
            condition = "⚠ " + alert;
        }
        paint.setColor(Color.WHITE);
        paint.setTextSize(40f);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        paint.setTextAlign(Paint.Align.CENTER);
        String weatherLine = safeCity + (TextUtils.isEmpty(temperature) ? "" : "  " + temperature + "°C");
        canvas.drawText(trimToWidth(weatherLine, paint, 500f), 540f, 425f, paint);
        canvas.drawText(trimToWidth(TextUtils.isEmpty(condition) ? "Đang cập nhật" : condition,
                paint, 520f), 540f, 475f, paint);

        drawAnimation(context, canvas, now.getTimeInMillis(), paint);
        return bitmap;
    }

    private static void drawAnimation(Context context, Canvas canvas, long time, Paint paint) {
        drawAnimated(context, canvas, "clock_anim_0", 500, 320, -80, 80,
                120 + 90 * (float) Math.sin(time / 500.0), time / 50f, paint);
        drawAnimated(context, canvas, "clock_anim_1", 600, 350, 70, 50,
                120 + 90 * (float) Math.sin(time / 600.0), -time / 60f, paint);
        drawAnimated(context, canvas, "clock_anim_2", 370, 310, 70, 50,
                120 + 90 * (float) Math.sin(time / 700.0), time / 80f, paint);
        drawAnimated(context, canvas, "clock_anim_3", 450, 370, 80, -90,
                120 + 90 * (float) Math.sin(time / 800.0), -time / 70f, paint);
    }

    private static void drawAnimated(Context context, Canvas canvas, String name, int x, int y,
                                     int centerX, int centerY, float alpha, float angle, Paint paint) {
        int id = resource(context, name);
        Bitmap bitmap = load(context, id);
        if (bitmap == null) {
            return;
        }
        paint.setAlpha(Math.max(0, Math.min(255, Math.round(alpha))));
        canvas.save();
        canvas.rotate(angle, x + centerX, y + centerY);
        canvas.drawBitmap(bitmap, x, y, paint);
        canvas.restore();
        paint.setAlpha(255);
    }

    private static void drawCentered(Context context, Canvas canvas, int id, int centerX, int top,
                                     Paint paint) {
        Bitmap bitmap = load(context, id);
        if (bitmap == null) {
            return;
        }
        drawAsset(context, canvas, id, centerX - bitmap.getWidth() / 2, top, paint);
    }

    private static void drawAsset(Context context, Canvas canvas, int id, int left, int top,
                                  Paint paint) {
        Bitmap bitmap = load(context, id);
        if (bitmap != null) {
            paint.setAlpha(255);
            canvas.drawBitmap(bitmap, left, top, paint);
        }
    }

    private static Bitmap load(Context context, int id) {
        if (id == 0) {
            return null;
        }
        synchronized (CACHE) {
            Bitmap cached = CACHE.get(id);
            if (cached == null) {
                cached = BitmapFactory.decodeResource(context.getResources(), id);
                if (cached != null) {
                    CACHE.put(id, cached);
                }
            }
            return cached;
        }
    }

    private static int resource(Context context, String name) {
        return context.getResources().getIdentifier(name, "drawable", context.getPackageName());
    }

    private static int sourceWeatherId(String icon) {
        if ("rain".equals(icon)) return 4;
        if ("storm".equals(icon)) return 7;
        if ("cloud".equals(icon)) return 2;
        return 0;
    }

    private static String trimToWidth(String value, Paint paint, float maxWidth) {
        if (paint.measureText(value) <= maxWidth) {
            return value;
        }
        String result = value;
        while (result.length() > 3 && paint.measureText(result + "…") > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result + "…";
    }

    private static String twoDigits(int value) {
        return value < 10 ? "0" + value : String.valueOf(value);
    }
}
