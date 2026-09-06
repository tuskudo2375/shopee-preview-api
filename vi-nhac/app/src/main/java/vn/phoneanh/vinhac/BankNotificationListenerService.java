package titus.expenseassistant;

import android.app.Notification;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.os.Bundle;

/** Optional listener that turns supported debit notifications into local expenses. */
public class BankNotificationListenerService extends NotificationListenerService {
    @Override public void onNotificationPosted(StatusBarNotification notification) {
        if (notification == null || getPackageName().equals(notification.getPackageName())) return;
        Notification source = notification.getNotification();
        if (source == null || source.extras == null) return;

        Bundle extras = source.extras;
        String title = value(extras, Notification.EXTRA_TITLE);
        String body = value(extras, Notification.EXTRA_BIG_TEXT);
        if (body.isEmpty()) body = value(extras, Notification.EXTRA_TEXT);
        if (body.isEmpty()) body = value(extras, Notification.EXTRA_SUB_TEXT);

        BudgetStore store = new BudgetStore(this);
        ExpenseParser.Result parsed = NotificationExpenseParser.parse(
                title, body, notification.getPackageName(), store.categories(), store.sources());
        if (parsed == null) return;

        // The notification key normally stays the same when a bank edits a pending
        // notification into a completed one, so do not create a second expense.
        String eventId = notification.getPackageName() + "|" + notification.getKey() + "|" + parsed.amount;
        String raw = title.isEmpty() ? body : title + " • " + body;
        if (store.addFromNotification(eventId, parsed, raw, parsed.source)) {
            NotificationHelper.refresh(this);
        }
    }

    private static String value(Bundle extras, String key) {
        CharSequence value = extras.getCharSequence(key);
        return value == null ? "" : value.toString().trim();
    }
}
