package titus.expenseassistant;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.util.LinkedHashSet;

/** Optional listener; parsing and notification content stay on the device. */
public class BankNotificationListenerService extends NotificationListenerService {
    private static BankNotificationListenerService connected;
    private static String lastScan = "Chưa quét thông báo";

    static boolean isConnected() { return connected != null; }
    static String scanStatus() { return lastScan; }

    static String rescan(Context context) {
        if (connected != null) return connected.scanActive();
        reconnect(context);
        return "Đang chờ kết nối. Nếu chưa kết nối, hãy tắt/bật lại quyền đọc thông báo rồi quay lại app.";
    }

    static void reconnect(Context context) {
        if (connected == null) {
            try { requestRebind(new ComponentName(context, BankNotificationListenerService.class)); }
            catch (RuntimeException ignored) { }
        }
    }

    @Override public void onListenerConnected() {
        super.onListenerConnected();
        connected = this;
        scanActive();
    }

    @Override public void onListenerDisconnected() {
        if (connected == this) connected = null;
        super.onListenerDisconnected();
        reconnect(this);
    }

    @Override public void onDestroy() {
        if (connected == this) connected = null;
        super.onDestroy();
    }

    private String scanActive() {
        try {
            StatusBarNotification[] active = getActiveNotifications();
            int added = 0;
            int checked = 0;
            if (active != null) for (StatusBarNotification item : active) {
                if (getPackageName().equals(item.getPackageName())) continue;
                checked++;
                if (capture(item)) added++;
            }
            lastScan = "Đã quét " + checked + " thông báo • Thêm " + added + " khoản chi mới";
        } catch (RuntimeException e) {
            lastScan = "Chưa đọc được thông báo. Hãy tắt/bật lại quyền đọc thông báo.";
        }
        return lastScan;
    }

    @Override public void onNotificationPosted(StatusBarNotification notification) {
        capture(notification);
    }

    private boolean capture(StatusBarNotification notification) {
        if (notification == null || getPackageName().equals(notification.getPackageName())) return false;
        Notification source = notification.getNotification();
        if (source == null || source.extras == null || (source.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return false;
        try {
            Bundle extras = source.extras;
            String title = value(extras, Notification.EXTRA_TITLE);
            LinkedHashSet<String> parts = new LinkedHashSet<>();
            String big = value(extras, Notification.EXTRA_BIG_TEXT);
            String shortText = value(extras, Notification.EXTRA_TEXT);
            if (!big.isEmpty()) parts.add(big);
            if (!shortText.isEmpty() && !big.contains(shortText)) parts.add(shortText);
            CharSequence[] lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
            if (lines != null) for (CharSequence line : lines) {
                if (line != null && !big.contains(line.toString())) parts.add(line.toString());
            }
            String sub = value(extras, Notification.EXTRA_SUB_TEXT);
            if (!sub.isEmpty()) parts.add(sub);
            String body = String.join("\n", parts);
            BudgetStore store = new BudgetStore(this);
            ExpenseParser.Result parsed = NotificationExpenseParser.parse(
                    title, body, notification.getPackageName(), store.categories(), store.sources());
            if (parsed == null) return false;

            long time = source.when > 0 && source.when <= notification.getPostTime()
                    ? source.when : notification.getPostTime();
            String legacyId = notification.getPackageName() + "|" + notification.getKey() + "|" + parsed.amount;
            String eventId = legacyId + "|" + time;
            String raw = title.isEmpty() ? body : title + "\n" + body;
            if (!store.addFromNotification(eventId, legacyId, parsed, raw, parsed.source, time)) return false;
            NotificationHelper.refresh(this);
            return true;
        } catch (RuntimeException ignored) { return false; }
    }

    private static String value(Bundle extras, String key) {
        CharSequence value = extras.getCharSequence(key);
        return value == null ? "" : value.toString().trim();
    }
}
