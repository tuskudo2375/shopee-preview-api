package titus.expenseassistant;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;

final class NotificationHelper {
    static final String STATUS_CHANNEL = "budget_status";
    static final String ALERT_CHANNEL = "expense_reminders";
    static final String INPUT_KEY = "quick_expense";
    static final int STATUS_ID = 1201;

    static void channels(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        NotificationChannel status = new NotificationChannel(STATUS_CHANNEL, "Ngân sách thường trực", NotificationManager.IMPORTANCE_LOW);
        status.setDescription("Hiện số tiền còn lại và chi tiêu hôm nay"); status.setShowBadge(false);
        NotificationChannel alert = new NotificationChannel(ALERT_CHANNEL, "Nhắc nhập chi tiêu", NotificationManager.IMPORTANCE_HIGH);
        alert.setDescription("Nhắc lúc 10:00, 13:00 và 21:30"); alert.enableVibration(true);
        nm.createNotificationChannel(status); nm.createNotificationChannel(alert);
    }

    static Notification status(Context c) {
        BudgetStore s = new BudgetStore(c);
        boolean over = s.todaySpent() > s.todayAllowance() && s.todaySpent() > 0;
        Intent open = new Intent(c, MainActivity.class);
        PendingIntent content = PendingIntent.getActivity(c, 1, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent deletedIntent = new Intent(c, PersistentNotificationReceiver.class).setAction(PersistentNotificationReceiver.ACTION_RESTORE);
        PendingIntent deleted = PendingIntent.getBroadcast(c, 4, deletedIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent replyIntent = new Intent(c, QuickExpenseReceiver.class);
        PendingIntent reply = PendingIntent.getBroadcast(c, 2, replyIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        RemoteInput input = new RemoteInput.Builder(INPUT_KEY).setLabel("Ví dụ: ăn trưa 30k").build();
        Notification.Action action = new Notification.Action.Builder(R.drawable.ic_wallet, "NHẬP NHANH", reply).addRemoteInput(input).build();
        int daysRemaining = s.daysRemainingInclusive();
        String title = s.budget() == 0 ? "Chạm để đặt ngân sách tháng" : "Còn " + Format.money(s.remaining()) + " trong " + daysRemaining + " ngày";
        String line = "Hôm nay: " + Format.money(s.todaySpent()) + " • Mức nên chi: " + Format.money(s.todayAllowance());
        if (over) line = "⚠ Đã vượt mức hôm nay • " + line;
        Notification notification = new Notification.Builder(c, STATUS_CHANNEL)
                .setSmallIcon(R.drawable.ic_wallet).setContentTitle(title).setContentText(line)
                .setStyle(new Notification.BigTextStyle().bigText(line + "\nNhập như: trà sữa 50k"))
                .setContentIntent(content).addAction(action).setOngoing(true).setOnlyAlertOnce(true)
                .setAutoCancel(false).setDeleteIntent(deleted).setShowWhen(false).setLocalOnly(true)
                .setVisibility(Notification.VISIBILITY_PRIVATE).setColor(over ? Color.RED : Color.rgb(26, 127, 75))
                .setCategory(Notification.CATEGORY_STATUS).build();
        notification.flags |= Notification.FLAG_ONGOING_EVENT | Notification.FLAG_NO_CLEAR;
        return notification;
    }

    static void reminder(Context c) {
        BudgetStore s = new BudgetStore(c);
        boolean over = s.todaySpent() > s.todayAllowance() && s.todaySpent() > 0;
        Intent open = new Intent(c, MainActivity.class);
        PendingIntent content = PendingIntent.getActivity(c, 3, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(c, ALERT_CHANNEL).setSmallIcon(R.drawable.ic_wallet)
                .setContentTitle(over ? "Bạn đang vượt mức chi hôm nay" : "Nhớ nhập chi tiêu nha")
                .setContentText("Đã chi " + Format.money(s.todaySpent()) + " • Còn " + Format.money(s.remaining()) + " trong " + s.daysRemainingInclusive() + " ngày")
                .setContentIntent(content).setAutoCancel(true).setColor(Color.RED).build();
        c.getSystemService(NotificationManager.class).notify(1202, n);
    }

    static void refresh(Context c) { c.getSystemService(NotificationManager.class).notify(STATUS_ID, status(c)); }
}
