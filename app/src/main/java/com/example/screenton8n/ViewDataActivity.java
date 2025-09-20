package com.example.screenton8n;

import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONException;
import org.json.JSONObject;

public class ViewDataActivity extends AppCompatActivity {

    private static final String PREFS_RESULTS = "upload_results";
    private static String keyForJob(String jobId) { return "result_" + jobId; }

    private TextView tvTrackId;
    private TextView tvStatus;
    private EditText etMessage;
    private TextView tvJson;
    private Button btnRefresh, btnSave, btnClose;

    private String jobId = "";
    private String status = "";
    private String incomingMessage = "";
    private String lastRawJson = "{}";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_data);

        tvTrackId = findViewById(R.id.tvTrackId);
        tvStatus  = findViewById(R.id.tvStatus);
        etMessage = findViewById(R.id.etMessage);
        tvJson    = findViewById(R.id.tvJson);
        btnRefresh = findViewById(R.id.btnRefresh);
        btnSave    = findViewById(R.id.btnSave);
        btnClose   = findViewById(R.id.btnClose);

        Uri data = getIntent() != null ? getIntent().getData() : null;
        if (data != null) {
            jobId = safe(data.getQueryParameter("jobId"));
            status = safe(data.getQueryParameter("status"));
            incomingMessage = safe(data.getQueryParameter("message"));
        }

        tvTrackId.setText(emptyToDash(jobId));
        tvStatus.setText(emptyToDash(status));
        if (!TextUtils.isEmpty(incomingMessage)) {
            etMessage.setText(incomingMessage);
        }

        loadAndRenderFromPrefs();

        btnRefresh.setOnClickListener(v -> loadAndRenderFromPrefs());

        btnSave.setOnClickListener(v -> {
            String userNote = etMessage.getText() != null ? etMessage.getText().toString() : "";
            if (TextUtils.isEmpty(jobId)) {
                Toast.makeText(this, "Missing jobId – cannot save", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                JSONObject root = tryParseJson(lastRawJson);
                JSONObject result = root.optJSONObject("result");
                if (result == null) {
                    result = new JSONObject();
                    root.put("result", result);
                }
                result.put("notes", userNote);

                String pretty = root.toString(2);
                SharedPreferences sp = getSharedPreferences(PREFS_RESULTS, MODE_PRIVATE);
                sp.edit().putString(keyForJob(jobId), pretty).apply();

                lastRawJson = pretty;
                tvJson.setText(pretty);

                Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
            } catch (JSONException e) {
                Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });

        btnClose.setOnClickListener(v -> finish());
    }

    private void loadAndRenderFromPrefs() {
        if (TextUtils.isEmpty(jobId)) {
            tvJson.setText("No jobId in deep link.");
            return;
        }

        SharedPreferences sp = getSharedPreferences(PREFS_RESULTS, MODE_PRIVATE);
        String raw = sp.getString(keyForJob(jobId), null);

        if (TextUtils.isEmpty(raw)) {
            tvJson.setText("No data found for jobId: " + jobId);
            return;
        }

        lastRawJson = raw;
        tvJson.setText(prettyOrRaw(raw));

        if (TextUtils.isEmpty(etMessage.getText())) {
            try {
                JSONObject root = tryParseJson(raw);
                String summary = root.optString("summary", "");
                String notes = "";
                JSONObject result = root.optJSONObject("result");
                if (result != null) notes = result.optString("notes", "");
                String candidate = !TextUtils.isEmpty(summary) ? summary : notes;
                if (!TextUtils.isEmpty(candidate)) etMessage.setText(candidate);
            } catch (Exception ignore) {}
        }
    }

    private String safe(String s) { return s == null ? "" : s; }
    private String emptyToDash(String s) { return TextUtils.isEmpty(s) ? "-" : s; }

    private JSONObject tryParseJson(String raw) throws JSONException {
        return new JSONObject(raw);
    }

    private String prettyOrRaw(String raw) {
        try { return new JSONObject(raw).toString(2); }
        catch (Exception e) { return raw; }
    }
}
