package vn.phoneanh.vinhac;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import java.time.ZonedDateTime;

final class ReminderScheduler {
    private static final int[][] TIMES = {{10,0}, {13,0}, {21,30}};
    static void schedule(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        ZonedDateTime now = ZonedDateTime.now();
        for (int i = 0; i < TIMES.length; i++) {
            ZonedDateTime next = now.withHour(TIMES[i][0]).withMinute(TIMES[i][1]).withSecond(0).withNano(0);
            if (!next.isAfter(now)) next = next.plusDays(1);
            Intent intent = new Intent(c, ReminderReceiver.class).putExtra("slot", i);
            PendingIntent pi = PendingIntent.getBroadcast(c, 100 + i, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            if (android.os.Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), pi);
            else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), pi);
        }
    }
}
