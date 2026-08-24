package titus.expenseassistant;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class BudgetNotificationService extends Service {
    @Override public void onCreate() { super.onCreate(); NotificationHelper.channels(this); startForeground(NotificationHelper.STATUS_ID, NotificationHelper.status(this)); }
    @Override public int onStartCommand(Intent intent, int flags, int startId) { NotificationHelper.refresh(this); ReminderScheduler.schedule(this); return START_STICKY; }
    @Override public IBinder onBind(Intent intent) { return null; }
}
