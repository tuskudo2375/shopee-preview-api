package titus.expenseassistant;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) { NotificationHelper.channels(context); NotificationHelper.reminder(context); ReminderScheduler.schedule(context); }
}
