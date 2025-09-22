package com.example.screenton8n;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Bridge שמקבל ACTION_SEND מה-Share Sheet ומעביר את התוכן ל-UploadService.
 * בגרסה זו אנו תומכים בשיתוף תמונה (image/*) שנשלחת ל-webhook.
 */
public class ShareBridgeActivity extends AppCompatActivity {

    private static final String TAG = "ShareBridge";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent in = getIntent();
        String action = in != null ? in.getAction() : null;
        String type   = in != null ? in.getType()   : null;

        Log.d(TAG, "onCreate action=" + action + " type=" + type);

        boolean started = false;

        if (Intent.ACTION_SEND.equals(action) && type != null) {
            // תמונות
            if (type.startsWith("image/")) {
                Uri imageUri = in.getParcelableExtra(Intent.EXTRA_STREAM);
                Log.d(TAG, "EXTRA_STREAM uri=" + imageUri);
                if (imageUri != null) {
                    // בונים Intent ל-UploadService עם אותו type ו-EXTRA_STREAM
                    Intent svc = new Intent(this, UploadService.class);
                    svc.setAction(Intent.ACTION_SEND);
                    svc.setType(type);
                    svc.putExtra(Intent.EXTRA_STREAM, imageUri);

                    // חשוב: לאפשר לשירות גישה לקריאת ה-URI
                    svc.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    try {
                        grantUriPermission(getPackageName(), imageUri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (Exception ignored) {}

                    try {
                        startForegroundService(svc);
                    } catch (IllegalStateException e) {
                        Log.w(TAG, "startForegroundService failed, fallback to startService", e);
                        startService(svc);
                    }
                    started = true;
                } else {
                    Log.e(TAG, "No image URI received in EXTRA_STREAM");
                }
            } else {
                Log.w(TAG, "Unsupported type for this build: " + type + " (expecting image/*)");
            }
        } else {
            Log.w(TAG, "Not an ACTION_SEND intent");
        }

        if (!started) {
            // אפשר להעיף toast קטן אם תרצה:
            // Utils.toast(this, "Nothing to share");
            Log.w(TAG, "Service not started (no valid payload)");
        }

        finish(); // לא משאירים את המסך הזה פתוח
    }
}
