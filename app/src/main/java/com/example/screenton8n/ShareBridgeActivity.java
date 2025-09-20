package com.example.screenton8n;

import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class ShareBridgeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            Intent in = getIntent();
            if (in == null) {
                Toast.makeText(this, "No share intent", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            String action = in.getAction();
            String type   = in.getType();

            if (!Intent.ACTION_SEND.equals(action) || type == null || !type.startsWith("image/")) {
                Toast.makeText(this, "Unsupported share type", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            Uri uri = in.getParcelableExtra(Intent.EXTRA_STREAM);
            if (uri == null) {
                ClipData cd = in.getClipData();
                if (cd != null && cd.getItemCount() > 0) {
                    uri = cd.getItemAt(0).getUri();
                }
            }

            if (uri == null) {
                Toast.makeText(this, "No image URI in share", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            String mime = type;
            try {
                ContentResolver cr = getContentResolver();
                String detected = cr.getType(uri);
                if (detected != null && !detected.equals("image/*")) {
                    mime = detected;
                } else {
                    String ext = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
                    if (ext != null) {
                        String byExt = MimeTypeMap.getSingleton()
                                .getMimeTypeFromExtension(ext.toLowerCase());
                        if (byExt != null) mime = byExt;
                    }
                }
                if (mime == null || mime.equals("image/*")) mime = "image/jpeg";
            } catch (Throwable ignore) {}

            Intent svc = new Intent(this, UploadService.class);
            svc.putExtra("uri", uri.toString());
            svc.putExtra("mime", mime);
            svc.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            grantUriPermission(getPackageName(), uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);

            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(svc);
            } else {
                startService(svc);
            }

            Toast.makeText(this, "Uploading…", Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            Toast.makeText(this, "Share error: " + t.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            finish();
        }
    }
}
