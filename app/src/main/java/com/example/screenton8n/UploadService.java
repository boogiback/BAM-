package com.example.screenton8n;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.DataOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Scanner;

public class UploadService extends Service {
    private static final String CHANNEL_ID = "UploadChannel";
    private static final String TAG = "UploadService";

    // IDs שונים לנוטיפיקציות
    private static final int FOREGROUND_ID = 1001;
    private static final int COMPLETE_ID   = 1002;

    // טיימאאוטים ארוכים כדי לאפשר ל-Make זמן עיבוד
    private static final int CONNECT_TIMEOUT_MS = 15000;   // 15s
    private static final int READ_TIMEOUT_MS    = 120000;  // 2min (אפשר להגדיל ל-180000)

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Uri imageUri = intent != null ? intent.getParcelableExtra(Intent.EXTRA_STREAM) : null;

        // נוטיפיקציה של Uploading… מיד עם התחלת השירות
        startForeground(FOREGROUND_ID, buildUploadingNotification());

        if (imageUri != null) {
            new Thread(() -> doUpload(imageUri)).start();
        } else {
            finishWithCompleteNotification(false, "No image URI");
        }
        return START_NOT_STICKY;
    }

    private void doUpload(Uri imageUri) {
        HttpURLConnection conn = null;
        try {
            // שליפת ה־webhook
            SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
            String incomingUrl = prefs.getString(
                    "incoming_webhook",
                    "https://hook.eu2.make.com/3whnaefpngyp1nwsa3ht1vt9pob17reg"
            );
            if (incomingUrl == null || incomingUrl.isEmpty()) {
                finishWithCompleteNotification(false, "Webhook not set");
                return;
            }

            Log.d(TAG, "Uploading to: " + incomingUrl);

            // הכנת החיבור
            URL url = new URL(incomingUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestMethod("POST");
            conn.setDoInput(true);
            conn.setDoOutput(true);

            // multipart form-data (שדה: file)
            String boundary = "----BAMBoundary" + System.currentTimeMillis();
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

            DataOutputStream dos = new DataOutputStream(conn.getOutputStream());
            dos.writeBytes("--" + boundary + "\r\n");
            dos.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"image.jpg\"\r\n");
            dos.writeBytes("Content-Type: image/jpeg\r\n\r\n");

            InputStream is = getContentResolver().openInputStream(imageUri);
            byte[] buffer = new byte[8192];
            int bytesRead;
            long total = 0;
            while ((bytesRead = is.read(buffer)) != -1) {
                dos.write(buffer, 0, bytesRead);
                total += bytesRead;
            }
            is.close();
            dos.writeBytes("\r\n--" + boundary + "--\r\n");
            dos.flush();
            dos.close();

            Log.d(TAG, "Wrote image bytes=" + total);

            int code = conn.getResponseCode();
            Log.d(TAG, "Response code: " + code);

            InputStream respStream = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            String respBody = null;
            if (respStream != null) {
                try (Scanner sc = new Scanner(respStream, "UTF-8")) {
                    sc.useDelimiter("\\A");
                    respBody = sc.hasNext() ? sc.next() : "";
                }
            }
            Log.d(TAG, "Response body: " + (respBody == null ? "<null>" : respBody));

            boolean success = code >= 200 && code < 300;
            if (success) {
                boolean saved = tryParseAndSaveLead(respBody);
                if (!saved) {
                    // אם אין JSON/לא ניתן לפרסר — ניצור כרטיס מינימלי כדי שהיוזר יראה “הגיע”
                    createFallbackLead();
                }
            }

            finishWithCompleteNotification(success,
                    success ? "Upload complete" : ("Upload failed (HTTP " + code + ")"));

        } catch (Exception e) {
            Log.e(TAG, "Upload error", e);
            finishWithCompleteNotification(false, "Upload error: " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** נוטיפיקציה הראשונית של Uploading… */
    private Notification buildUploadingNotification() {
        Intent open = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(
                this, 10, open,
                Build.VERSION.SDK_INT >= 23 ?
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE :
                        PendingIntent.FLAG_UPDATE_CURRENT
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Uploading…")
                .setContentText("Sending to webhook")
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(pi)
                .build();
    }

    /** סיום Foreground + הצגת נוטיפיקציית “הושלם/כשל” */
    private void finishWithCompleteNotification(boolean success, String detail) {
        try {
            stopForeground(true);
        } catch (Exception ignored) {}

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        Intent open = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(
                this, 11, open,
                Build.VERSION.SDK_INT >= 23 ?
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE :
                        PendingIntent.FLAG_UPDATE_CURRENT
        );

        Notification done = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(success ? "Upload complete" : "Upload failed")
                .setContentText(detail)
                .setSmallIcon(success ? android.R.drawable.stat_sys_upload_done
                        : android.R.drawable.stat_notify_error)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build();

        if (nm != null) nm.notify(COMPLETE_ID, done);

        // טוסט קטן
        new android.os.Handler(getMainLooper()).post(() ->
                Toast.makeText(getApplicationContext(), detail, Toast.LENGTH_SHORT).show());

        stopSelf();
    }

    // ===================== פרסינג ושמירת לידים =====================

    /**
     * מנסה לפרסר את JSON התשובה מ-Make ולשמור כ-Lean חדש.
     * נתמך:
     * - אובייקט יחיד עם שדות result/summary/ts וכו'
     * - מערך JSON → לוקחים את האיבר הראשון
     * מחזיר true אם נשמר Lead בפועל.
     */
    private boolean tryParseAndSaveLead(String respBody) {
        if (respBody == null || respBody.trim().isEmpty()) return false;

        try {
            JSONObject root;
            String s = respBody.trim();

            // מערך → ניקח את הראשון
            if (s.startsWith("[")) {
                JSONArray arr = new JSONArray(s);
                if (arr.length() == 0) return false;
                root = arr.getJSONObject(0);
            } else {
                root = new JSONObject(s);
            }

            // לעתים הנתונים הפנימיים תחת result, ולעתים ישירות באובייקט
            JSONObject data = root.optJSONObject("result");
            if (data == null) data = root;

            Lead L = new Lead();
            L.firstName = data.optString("first_name", "");
            L.lastName  = data.optString("last_name",  "");
            L.phone     = data.optString("phone",      "");
            L.email     = data.optString("email",      "");
            L.type      = data.optString("type",       "");
            L.notes     = data.optString("notes",      "");
            L.summary   = root.optString("summary",
                    (L.firstName + " " + L.lastName).trim().isEmpty() ? "New lead" :
                            (L.firstName + " " + L.lastName).trim());

            long ts = root.optLong("ts", 0);
            if (ts == 0) ts = data.optLong("ts", 0);
            L.ts = ts > 0 ? ts : System.currentTimeMillis();

            ArrayList<Lead> leads = LeadStore.loadLeads(this);
            leads.add(0, L); // לראש הרשימה
            LeadStore.saveLeads(this, leads);

            Log.d(TAG, "Lead saved from JSON");
            return true;

        } catch (JSONException je) {
            Log.w(TAG, "Response not JSON or schema mismatch", je);
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Failed to save lead", e);
            return false;
        }
    }

    /** אם אין JSON אבל קיבלנו 2xx — ניצור כרטיס מינימלי כדי שתראה שקלטנו משהו */
    private void createFallbackLead() {
        try {
            Lead L = new Lead();
            L.summary   = "Upload completed";
            L.firstName = "";
            L.lastName  = "";
            L.phone     = "";
            L.email     = "";
            L.type      = "";
            L.notes     = "";
            L.ts        = System.currentTimeMillis();

            ArrayList<Lead> leads = LeadStore.loadLeads(this);
            leads.add(0, L);
            LeadStore.saveLeads(this, leads);

            Log.d(TAG, "Fallback lead saved");
        } catch (Exception e) {
            Log.e(TAG, "Failed to save fallback lead", e);
        }
    }

    // ===============================================================

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Upload Service", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    @Nullable @Override
    public IBinder onBind(Intent intent) { return null; }
}
