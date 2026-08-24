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
        PendingResult pending=goAsync(); String raw=text.toString();
        new Thread(()->{String category=GeminiClassifier.classify(context,raw,parsed.category);ExpenseParser.Result smart=new ExpenseParser.Result(parsed.amount,category,parsed.note);new BudgetStore(context).add(smart,raw);NotificationHelper.refresh(context);new android.os.Handler(android.os.Looper.getMainLooper()).post(()->Toast.makeText(context,"Đã thêm "+Format.money(smart.amount)+" • "+smart.category,Toast.LENGTH_SHORT).show());pending.finish();}).start();
    }
}
