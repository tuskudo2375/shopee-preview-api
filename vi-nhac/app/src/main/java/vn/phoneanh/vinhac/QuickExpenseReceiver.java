package titus.expenseassistant;

import android.app.RemoteInput;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

public class QuickExpenseReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        Bundle results = RemoteInput.getResultsFromIntent(intent);
        if (results == null) return;
        CharSequence text = results.getCharSequence(NotificationHelper.INPUT_KEY);
        if (text == null) return;
        ExpenseParser.Result parsed = ExpenseParser.parse(text.toString());
        if (parsed.amount <= 0) { Toast.makeText(context, "Chưa đọc được số tiền", Toast.LENGTH_LONG).show(); return; }
        new BudgetStore(context).add(parsed, text.toString());
        NotificationHelper.refresh(context);
        Toast.makeText(context, "Đã thêm " + Format.money(parsed.amount) + " • " + parsed.category, Toast.LENGTH_SHORT).show();
    }
}
