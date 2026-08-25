package titus.vietclockwidget;

import android.Manifest;
import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int LOCATION_REQUEST = 4101;
    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private TextView statusView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setResult(RESULT_CANCELED);
        appWidgetId = getIntent().getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
        );
        showScreen();

        if (hasLocationPermission()) {
            finishConfiguration();
        } else {
            requestLocation();
        }
    }

    private void showScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(48, 72, 48, 48);
        root.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText("Đồng hồ Việt");
        title.setTextColor(Color.rgb(220, 38, 38));
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView description = new TextView(this);
        description.setText("Hiển thị giờ, thời tiết đúng quận/huyện theo vị trí chính xác và lịch âm Việt Nam trên widget 4×2.");
        description.setTextColor(Color.DKGRAY);
        description.setTextSize(16);
        description.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(-1, -2);
        descriptionParams.topMargin = 24;
        root.addView(description, descriptionParams);

        statusView = new TextView(this);
        statusView.setText("Đang kiểm tra quyền vị trí…");
        statusView.setTextColor(Color.GRAY);
        statusView.setTextSize(14);
        statusView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, -2);
        statusParams.topMargin = 24;
        root.addView(statusView, statusParams);

        Button button = new Button(this);
        button.setText("Cho phép vị trí và làm mới widget");
        button.setOnClickListener(view -> requestLocation());
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(-1, -2);
        buttonParams.topMargin = 28;
        root.addView(button, buttonParams);

        setContentView(root);
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocation() {
        if (statusView != null) {
            statusView.setText("Vui lòng chọn Vị trí chính xác để thời tiết nhận đúng quận/huyện.");
        }
        requestPermissions(new String[]{
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
        }, LOCATION_REQUEST);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != LOCATION_REQUEST) {
            return;
        }
        if (hasLocationPermission()) {
            finishConfiguration();
        } else if (statusView != null) {
            statusView.setText("Chưa có quyền vị trí. Có thể cấp lại trong Cài đặt > Ứng dụng > Đồng hồ Việt.");
        }
    }

    private void finishConfiguration() {
        VietnameseClockWidget.requestRefresh(this);
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            Intent result = new Intent();
            result.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            setResult(RESULT_OK, result);
            finish();
        } else if (statusView != null) {
            statusView.setText("Đã bật vị trí. Hãy thêm widget Đồng hồ Việt 4×2 vào màn hình chính.");
        }
    }
}
