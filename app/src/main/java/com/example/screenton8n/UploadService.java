package com.example.screenton8n;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.MimeTypeMap;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;

public class UploadService extends Service {
    private static final String TAG = "UPLOAD";
    private static final String CHANNEL_ID = "uploads_v2";
    private static final int NOTIF_UPLOAD_ID = 1001; // foreground
    private static final int NOTIF_RESULT_ID = 1002; // "Data ready"

    public static final String PREFS_SETTINGS = "app_settings";
    public static final String KEY_WEBHOOK_URL = "webhook_url";

    private static final String PREFS_RESULTS = "upload_results";
    private static String keyForJob(String jobId) { return "result_" + jobId; }

    private final OkHttpClient http = new OkHttpClient.Builder()
            .retryOnConnectionFailure(true)
            .connectTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS)
            .build();

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        ensureChannel();

        Notification n = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentTitle("Share Uploader")
                .setContentText("Uploading…")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build();
        startForeground(NOTIF_UPLOAD_ID, n);

        new Thread(() -> doUpload(intent)).start();
        return START_NOT_STICKY;
    }

    private void doUpload(@Nullable Intent intent) {
        String uiText;
        try {
            if (intent == null) throw new Exception("Intent is null");
            String uriStr = intent.getStringExtra("uri");
            String mimeIn = intent.getStringExtra("mime");
            if (uriStr == null) throw new Exception("Missing URI");

            Uri uri = Uri.parse(uriStr);

            String webhookUrl = getSavedWebhookUrl();
            if (TextUtils.isEmpty(webhookUrl) ||
                    !(webhookUrl.startsWith("http://") || webhookUrl.startsWith("https://"))) {
                throw new Exception("Webhook URL not set/invalid.");
            }

            String mime = resolveMime(uri, mimeIn);
            if (!mime.startsWith("image/")) mime = "image/jpeg";
            String fileName = buildFileName(uri, mime);
            String userId = getAndroidIdSafe();
            String jobId = UUID.randomUUID().toString();

            Log.d(TAG, "Start upload | url=" + webhookUrl + " | jobId=" + jobId +
                    " | mime=" + mime + " | file=" + fileName);

            final MediaType mediaTypeFinal = MediaType.parse(mime);
            ContentResolver cr = getContentResolver();

            RequestBody streamBody = new RequestBody() {
                @Override public MediaType contentType() { return mediaTypeFinal; }
                @Override public void writeTo(BufferedSink sink) throws IOException {
                    try (InputStream in = cr.openInputStream(uri)) {
                        if (in == null) throw new IOException("Cannot open stream");
                        byte[] buf = new byte[8192];
                        int r;
                        while ((r = in.read(buf)) != -1) {
                            sink.write(buf, 0, r);
                        }
                    }
                }
                @Override public long contentLength() { return -1; }
            };

            MultipartBody body = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", fileName, streamBody)
                    .addFormDataPart("mime", mime)
                    .addFormDataPart("fileName", fileName)
                    .addFormDataPart("userId", userId)
                    .addFormDataPart("jobId", jobId)
                    .addFormDataPart("timestamp", String.valueOf(System.currentTimeMillis()))
                    .build();

            Request req = new Request.Builder().url(webhookUrl).post(body).build();

            int code;
            String responseBody;
            try (Response resp = http.newCall(req).execute()) {
                code = resp.code();
                responseBody = (resp.body() != null) ? resp.body().string() : "";
            }
            Log.d(TAG, "HTTP " + code + " | body=" + responseBody);

            if (code >= 200 && code < 300 && responseBody != null && !responseBody.isEmpty()) {
                JSONObject j = new JSONObject(responseBody);
                boolean ok = j.optBoolean("ok", true);
                String returnedJobId = j.optString("jobId", jobId);
                String summary = j.optString("summary", ok ? "Data ready" : "Processed");

                SharedPreferences sp = getSharedPreferences(PREFS_RESULTS, MODE_PRIVATE);
                sp.edit().putString(keyForJob(returnedJobId), j.toString()).apply();

                String deepLink = buildDeepLink(returnedJobId, ok ? "success" : "processed", summary);
                postViewDataNotification(summary, deepLink); // show “Data ready”

                stopForeground(false);
                stopSelf();
                return;
            } else {
                uiText = "Failed (" + code + ")";
            }
        } catch (JSONException je) {
            Log.e(TAG, "JSON parse error", je);
            uiText = "Response parse error";
        } catch (Exception e) {
            Log.e(TAG, "Upload error", e);
            uiText = "Upload error: " + e.getMessage();
        }

        Notification done = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_upload_done)
                .setContentTitle("Share Uploader")
                .setContentText(uiText)
                .setAutoCancel(true)
                .setOngoing(false)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build();
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(NOTIF_UPLOAD_ID, done);

        stopForeground(false);
        stopSelf();
    }

    private void postViewDataNotification(String summary, String deepLink) {
        Intent viewIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(deepLink))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .setPackage(getPackageName());

        PendingIntent pi = PendingIntent.getActivity(
                this, 200, viewIntent,
                (Build.VERSION.SDK_INT >= 23)
                        ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                        : PendingIntent.FLAG_UPDATE_CURRENT
        );

        Notification n = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_upload_done)
                .setContentTitle("Data ready")
                .setContentText(summary)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(summary))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setOngoing(false)
                .build();

        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.notify(NOTIF_RESULT_ID, n); // different ID from the foreground
            Log.d(TAG, "posted 'Data ready' notification | deepLink=" + deepLink);
        }
    }

    private String buildDeepLink(String jobId, String status, String message) {
        String safeMsg = (message == null) ? "" : message;
        try { safeMsg = URLEncoder.encode(safeMsg, StandardCharsets.UTF_8.name()); } catch (Exception ignore) {}
        return "myapp://viewdata?jobId=" + jobId +
                "&status=" + status +
                "&message=" + safeMsg;
    }

    private String getSavedWebhookUrl() {
        SharedPreferences sp = getSharedPreferences(PREFS_SETTINGS, MODE_PRIVATE);
        return sp.getString(KEY_WEBHOOK_URL, "");
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch =
                    new NotificationChannel(CHANNEL_ID, "Uploads",
                            NotificationManager.IMPORTANCE_DEFAULT);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private String resolveMime(Uri uri, String fromIntent) {
        try {
            if (fromIntent != null && !fromIntent.equals("image/*"))
                return normalizeJpeg(fromIntent);

            String byCr = getContentResolver().getType(uri);
            if (byCr != null && !byCr.equals("image/*"))
                return normalizeJpeg(byCr);

            String ext = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
            if (ext != null) {
                String byExt = MimeTypeMap.getSingleton()
                        .getMimeTypeFromExtension(ext.toLowerCase(Locale.US));
                if (byExt != null) return normalizeJpeg(byExt);
            }
        } catch (Throwable ignore) {}
        return "image/jpeg";
    }

    private String normalizeJpeg(String mime) {
        if (mime == null) return "image/jpeg";
        if (mime.equalsIgnoreCase("image/jpg")) return "image/jpeg";
        return mime;
    }

    private String buildFileName(Uri uri, String mime) {
        String ext = "jpg";
        if (mime.equals("image/png")) ext = "png";
        else if (mime.equals("image/webp")) ext = "webp";
        else if (mime.equals("image/gif")) ext = "gif";

        String name = "screenshot." + ext;
        try {
            String last = uri.getLastPathSegment();
            if (last != null && last.contains(".")) {
                int dot = last.lastIndexOf('.');
                name = last.substring(0, dot + 1) + ext;
            }
        } catch (Throwable ignore) {}
        return name;
    }

    private String getAndroidIdSafe() {
        try {
            String id = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
            if (id != null && !id.trim().isEmpty()) return id;
        } catch (Throwable ignore) {}
        return "unknown";
    }
}
