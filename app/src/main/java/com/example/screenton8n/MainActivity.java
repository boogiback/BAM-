package com.example.screenton8n;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;

import java.util.Map;

public class MainActivity extends AppCompatActivity {

    public static final String PREFS_SETTINGS = "app_settings";
    public static final String KEY_WEBHOOK_URL = "webhook_url";

    private static final String RESULTS_PREFS = "upload_results";
    private static final String CHANNEL_ID = "uploads_v2";
    private static final int REQ_POST_NOTIFS = 42;

    private EditText etWebhook;
    private Button btnSave, btnTestNotif, btnRefreshLog;
    private TextView tvLog;

    private static final String DEFAULT_WEBHOOK =
            "https://hook.eu2.make.com/m9rf3dlyyjvrvmzq2qc89xcn9ce5p3mh";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etWebhook     = findViewById(R.id.etWebhook);
        btnSave       = findViewById(R.id.btnSave);
        btnTestNotif  = findViewById(R.id.btnTestNotif);
        btnRefreshLog = findViewById(R.id.btnRefreshLog);
        tvLog         = findViewById(R.id.tvLog);

        // Permission + channel for notifications
        maybeAskPostNotifications();
        ensureChannel();

        // Load webhook
        SharedPreferences sp = getSharedPreferences(PREFS_SETTINGS, MODE_PRIVATE);
        String saved = sp.getString(KEY_WEBHOOK_URL, DEFAULT_WEBHOOK);
        etWebhook.setText(saved);

        etWebhook.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                btnSave.setEnabled(isValidUrl(s));
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        btnSave.setEnabled(isValidUrl(saved));

        btnSave.setOnClickListener(v -> {
            String url = etWebhook.getText().toString().trim();
            if (!isValidUrl(url)) {
                Toast.makeText(this, "URL לא תקין", Toast.LENGTH_SHORT).show();
                return;
            }
            sp.edit().putString(KEY_WEBHOOK_URL, url).apply();
            Toast.makeText(this, "נשמר", Toast.LENGTH_SHORT).show();
        });

        // Test notification -> deep link to ViewDataActivity
        btnTestNotif.setOnClickListener(v -> showTestNotification());

        // Log of all results saved into SharedPreferences
        btnRefreshLog.setOnClickListener(v -> renderAllResults());
        renderAllResults();
    }

    private boolean isValidUrl(CharSequence s) {
        if (TextUtils.isEmpty(s)) return false;
        return Patterns.WEB_URL.matcher(s).matches()
                && (s.toString().startsWith("http://") || s.toString().startsWith("https://"));
    }

    private void maybeAskPostNotifications() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{ Manifest.permission.POST_NOTIFICATIONS }, REQ_POST_NOTIFS);
            }
        }
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch =
                    new NotificationChannel(CHANNEL_ID, "Uploads",
                            NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("Upload progress and results");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private void showTestNotification() {
        String deep = "myapp://viewdata?jobId=debug-456&status=success&message=Local%20test";
        PendingIntent pi = PendingIntent.getActivity(
                this, 999,
                new android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(deep))
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                | android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        .setPackage(getPackageName()),
                (Build.VERSION.SDK_INT >= 23)
                        ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                        : PendingIntent.FLAG_UPDATE_CURRENT
        );

        Notification n = new androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_upload_done)
                .setContentTitle("Data ready (LOCAL)")
                .setContentText("Tap to open ViewDataActivity")
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(4242, n);
        Toast.makeText(this, "נשלחה נוטיפיקציית בדיקה", Toast.LENGTH_SHORT).show();
    }

    private void renderAllResults() {
        SharedPreferences sp = getSharedPreferences(RESULTS_PREFS, MODE_PRIVATE);
        Map<String, ?> all = sp.getAll();
        if (all == null || all.isEmpty()) {
            tvLog.setText("No results yet.");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, ?> e : all.entrySet()) {
            String key = e.getKey();
            Object val = e.getValue();
            sb.append("• ").append(key).append("\n");
            String s = String.valueOf(val);
            sb.append(prettyOrRaw(s)).append("\n\n");
        }
        tvLog.setText(sb.toString());
    }

    private String prettyOrRaw(String raw) {
        try { return new JSONObject(raw).toString(2); }
        catch (Exception e) { return raw; }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_POST_NOTIFS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "נוטיפיקציות הופעלו", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "בלי הרשאה — ייתכן שנוטיפיקציות לא יופיעו", Toast.LENGTH_LONG).show();
            }
        }
    }
}
