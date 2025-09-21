package com.example.screenton8n;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.navigation.NavigationView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.DateFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * מסך ראשי:
 * - המבורגר להגדרת ה-Webhook
 * - כפתור Clear logs
 * - הצגת רשומות מ-SharedPreferences ("upload_results")
 * - כל Entry לחיץ ופותח את ViewDataActivity עם deep link (כמו בנוטיפיקציה)
 */
public class MainActivity extends AppCompatActivity {

    // ===== Storage keys =====
    public static final String PREFS_SETTINGS = "app_settings";
    public static final String KEY_WEBHOOK_URL = "webhook_url";
    public static final String RESULTS_PREFS  = "upload_results";

    // ברירת מחדל שהגדרת
    private static final String DEFAULT_WEBHOOK =
            "https://hook.eu2.make.com/3whnaefpngyp1nwsa3ht1vt9pob17reg";

    // מפתחות "מטא־דטה" שלא נציג
    private static final String[] META_KEYS = new String[]{
            "meta","headers","contentType","mimeType","filename","fileName",
            "size","length","path","url","source","request","response",
            "status","ok","success"
    };

    private androidx.drawerlayout.widget.DrawerLayout drawerLayout;
    private NavigationView navView;
    private MaterialToolbar toolbar;

    private LinearLayout logsContainer;  // מתוך content_main.xml (LinearLayout בתוך ScrollView)
    private TextView emptyView;          // טקסט "No logs yet"
    private View btnClearLogs;           // הכפתור בתחתית activity_main

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // לא לצבוע את ה-StatusBar בסגול (להשאיר שקוף/דיפולט)
        getWindow().setStatusBarColor(Color.TRANSPARENT);

        // ===== Views =====
        drawerLayout = findViewById(R.id.drawer_layout);
        navView      = findViewById(R.id.nav_view);
        toolbar      = findViewById(R.id.toolbar);

        // מתוך content_main.xml
        logsContainer = findViewById(R.id.logs_container);
        emptyView     = new TextView(this);
        emptyView.setText(R.string.no_logs_yet);
        emptyView.setPadding(dp(16), dp(24), dp(16), dp(24));
        emptyView.setGravity(Gravity.CENTER);

        // כפתור ניקוי
        btnClearLogs  = findViewById(R.id.btn_clear_logs);

        // ===== Toolbar + Drawer =====
        setSupportActionBar(toolbar);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar, R.string.nav_open, R.string.nav_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        navView.setNavigationItemSelectedListener(this::onNavigationItemSelected);

        // לא מציגים כתובת webhook בכותרת (ביקשת להסיר)

        // ===== Clear logs =====
        btnClearLogs.setOnClickListener(v -> {
            getSharedPreferences(RESULTS_PREFS, MODE_PRIVATE).edit().clear().apply();
            Toast.makeText(this, R.string.logs_cleared, Toast.LENGTH_SHORT).show();
            renderLogs();
        });

        renderLogs();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // מתעדכן אוטומטית כשחוזרים מ-ViewDataActivity
        renderLogs();
    }

    private boolean onNavigationItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.nav_configure_webhook) {
            showEditWebhookDialog();
        }
        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    private void showEditWebhookDialog() {
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint(R.string.webhook_hint);

        // ערך נוכחי
        SharedPreferences sp = getSharedPreferences(PREFS_SETTINGS, MODE_PRIVATE);
        String curr = sp.getString(KEY_WEBHOOK_URL, DEFAULT_WEBHOOK);
        input.setText(curr);

        new AlertDialog.Builder(this)
                .setTitle(R.string.edit_webhook_title)
                .setView(input)
                .setNegativeButton(R.string.cancel, (d, w) -> d.dismiss())
                .setPositiveButton(R.string.save, (d, w) -> {
                    String url = String.valueOf(input.getText()).trim();
                    if (!isValidUrl(url)) {
                        Toast.makeText(this, R.string.invalid_url, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    sp.edit().putString(KEY_WEBHOOK_URL, url).apply();
                    Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private boolean isValidUrl(CharSequence s) {
        return !TextUtils.isEmpty(s)
                && Patterns.WEB_URL.matcher(s).matches()
                && (s.toString().startsWith("http://") || s.toString().startsWith("https://"));
    }

    /**
     * קורא את כל הרשומות מ-SharedPreferences (RESULTS_PREFS),
     * ובונה UI דינמי: כל רשומה עם זמן קבלה + תוכן נתונים (ללא "מטא").
     * כל רשומה לחיצה: פותחת ViewDataActivity עם deep link (myapp://viewdata?...).
     */
    private void renderLogs() {
        logsContainer.removeAllViews();

        SharedPreferences sp = getSharedPreferences(RESULTS_PREFS, MODE_PRIVATE);
        Map<String, ?> all = sp.getAll();

        if (all == null || all.isEmpty()) {
            logsContainer.addView(emptyView);
            return;
        }

        // שומרים סדר הכנסה עד כמה שאפשר
        LinkedHashMap<String, Object> ordered = new LinkedHashMap<>(all);

        int index = 1;
        for (Map.Entry<String, Object> e : ordered.entrySet()) {
            String key = e.getKey();           // לדוגמה: result_<jobId>
            String raw = String.valueOf(e.getValue());

            JSONObject full = parseJsonSafe(raw);
            JSONObject data = filterOnlyData(full);
            long ts = extractTimestampOrNow(full);

            // מייצרים מכולת View לרשומה + מאזין לחיצה
            LinearLayout entry = buildEntryView(index++, key, ts, data, full);
            logsContainer.addView(entry);
        }
    }

    /** בונה View של רשומה + מאזין לחיצה שפותח את ViewDataActivity עם deep link */
    private LinearLayout buildEntryView(int number, String key, long ts, JSONObject data, JSONObject full) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(0, dp(8), 0, dp(10));
        container.setClickable(true);
        container.setBackgroundResource(android.R.drawable.list_selector_background);

        // כותרת
        TextView header = new TextView(this);
        header.setText(String.format(Locale.getDefault(), "===== LEAD #%d =====", number));
        header.setPadding(0, 0, 0, dp(4));
        header.setTextAppearance(android.R.style.TextAppearance_Medium);
        container.addView(header);

        // זמן
        TextView when = new TextView(this);
        when.setText(DateFormat.getDateTimeInstance().format(ts));
        when.setTextAppearance(android.R.style.TextAppearance_Small);
        container.addView(when);

        // מפתח
        TextView keyView = new TextView(this);
        keyView.setText("Key: " + key);
        keyView.setTextAppearance(android.R.style.TextAppearance_Small);
        container.addView(keyView);

        // גוף הנתונים (key: value)
        TextView body = new TextView(this);
        body.setPadding(0, dp(6), 0, 0);
        body.setText(jsonToBulletedLines(data));
        container.addView(body);

        // לחיצה: פותח myapp://viewdata?jobId=...&status=...&message=...
        container.setOnClickListener(v -> {
            String jobId  = full.optString("jobId", "");
            String status = full.optString("status", "");
            String msg    = full.optString("summary",
                    full.optString("message", ""));

            Uri.Builder ub = new Uri.Builder()
                    .scheme("myapp")
                    .authority("viewdata");
            if (!TextUtils.isEmpty(jobId))  ub.appendQueryParameter("jobId", jobId);
            if (!TextUtils.isEmpty(status)) ub.appendQueryParameter("status", status);
            if (!TextUtils.isEmpty(msg))    ub.appendQueryParameter("message", msg);

            Intent i = new Intent(Intent.ACTION_VIEW, ub.build());
            startActivity(i);
        });

        return container;
    }

    // ===== JSON helpers =====

    private JSONObject parseJsonSafe(String raw) {
        try { return new JSONObject(raw); }
        catch (Exception e) { return new JSONObject(); }
    }

    /** מנסה לחלץ זמן קבלה מתוך full (ts/timestamp). אם אין—עכשיו. */
    private long extractTimestampOrNow(JSONObject full) {
        long t = full.optLong("ts", 0);
        if (t == 0) t = full.optLong("timestamp", 0);
        if (t > 0) return t;
        return System.currentTimeMillis();
    }

    /** מסנן "מטא־דטה" מתוך אובייקט מלא ומשאיר רק נתונים להצגה. */
    private JSONObject filterOnlyData(JSONObject in) {
        try {
            JSONObject out = new JSONObject();
            JSONArray names = in.names();
            if (names != null) {
                for (int i = 0; i < names.length(); i++) {
                    String k = names.getString(i);
                    if (isMeta(k)) continue;
                    out.put(k, in.opt(k));
                }
            }
            return out;
        } catch (Exception ex) {
            JSONObject out = new JSONObject();
            try { out.put("value", in.toString()); } catch (Exception ignore) {}
            return out;
        }
    }

    private boolean isMeta(String key) {
        if (key == null) return true;
        if (key.startsWith("_")) return true;
        for (String m : META_KEYS) if (m.equalsIgnoreCase(key)) return true;
        return false;
    }

    /** מדפיס JSONObject כשורות "• key: value" */
    private CharSequence jsonToBulletedLines(JSONObject obj) {
        if (obj == null || obj.length() == 0) return "";
        StringBuilder sb = new StringBuilder();
        JSONArray names = obj.names();
        if (names != null) {
            for (int i = 0; i < names.length(); i++) {
                String k = names.optString(i);
                Object v = obj.opt(k);
                sb.append("• ").append(k).append(": ").append(String.valueOf(v)).append("\n");
            }
        }
        return sb.toString().trim();
    }

    private int dp(int value) {
        float d = getResources().getDisplayMetrics().density;
        return Math.round(value * d);
    }
}
