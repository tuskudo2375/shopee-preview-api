package titus.expenseassistant;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import java.time.Duration;
import java.time.Instant;

public class PersistentNotificationReceiver extends BroadcastReceiver {
    static final String ACTION_RESTORE = "titus.expenseassistant.RESTORE_STATUS";
    static final String ACTION_HEARTBEAT = "titus.expenseassistant.HEARTBEAT_STATUS";

    static void scheduleHeartbeat(Context context) {
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        Intent heartbeat = new Intent(context, PersistentNotificationReceiver.class).setAction(ACTION_HEARTBEAT);
        PendingIntent pending = PendingIntent.getBroadcast(context, 5, heartbeat, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        long first = Instant.now().plus(Duration.ofMinutes(15)).toEpochMilli();
        alarms.setInexactRepeating(AlarmManager.RTC_WAKEUP, first, 15 * 60 * 1000L, pending);
    }

    @Override public void onReceive(Context context, Intent intent) {
        NotificationHelper.channels(context);
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(new Intent(context, BudgetNotificationService.class));
        else context.startService(new Intent(context, BudgetNotificationService.class));
    }
}
