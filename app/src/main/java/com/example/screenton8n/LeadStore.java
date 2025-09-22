package com.example.screenton8n;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;

public class LeadStore {
    public static final String PREFS_SETTINGS = "app_settings";
    public static final String RESULTS_PREFS  = "upload_results";

    public static final String KEY_INCOMING_WEBHOOK = "incoming_webhook_url";
    public static final String KEY_OUTPUT_WEBHOOK   = "upload_webhook_url";
    public static final String KEY_DELETE_WEBHOOK   = "delete_webhook_url";

    public static final String DEFAULT_INCOMING_WEBHOOK =
            "https://hook.eu2.make.com/3whnaefpngyp1nwsa3ht1vt9pob17reg";
    public static final String DEFAULT_DELETE_WEBHOOK =
            "https://hook.eu2.make.com/wnt248rsa0x6fhrtoh64dk4brg6s1rft";

    // ===== Leads (שמירה חדשה) =====
    public static void saveLeads(Context ctx, ArrayList<Lead> leads) {
        JSONArray arr = new JSONArray();
        for (Lead l : leads) arr.put(l.toJson());
        prefsR(ctx).edit().putString("results_json", arr.toString()).apply();
    }

    public static ArrayList<Lead> loadLeads(Context ctx) {
        String raw = prefsR(ctx).getString("results_json", "[]");
        ArrayList<Lead> out = new ArrayList<Lead>();
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) out.add(Lead.fromJson(arr.getJSONObject(i)));
        } catch (JSONException ignored) {}
        return out;
    }

    // תאימות אחורה: טוען גם מפתחות ישנים result_*
    public static ArrayList<Lead> loadLeadsCompat(Context ctx) {
        ArrayList<Lead> out = new ArrayList<Lead>(loadLeads(ctx));
        Map<String, ?> all = prefsR(ctx).getAll();
        for (Map.Entry<String, ?> e : all.entrySet()) {
            String k = e.getKey();
            Object v = e.getValue();
            if (k != null && k.startsWith("result_") && v instanceof String) {
                try {
                    JSONObject j = new JSONObject((String) v);
                    Lead L = new Lead();
                    L.id      = k;
                    L.jobId   = j.optString("jobId", "");
                    L.summary = j.optString("summary", "");
                    L.ts      = System.currentTimeMillis();

                    JSONObject r = j.optJSONObject("result");
                    if (r == null) r = new JSONObject();
                    L.firstName = r.optString("first_name", "");
                    L.lastName  = r.optString("last_name",  "");
                    L.phone     = r.optString("phone",      "");
                    L.email     = r.optString("email",      "");
                    L.type      = r.optString("type",       "");
                    L.notes     = r.optString("notes",      "");

                    out.add(L);
                } catch (Exception ignored) {}
            }
        }
        out.sort(new Comparator<Lead>() {
            @Override
            public int compare(Lead a, Lead b) {
                // סדר יורד לפי ts
                return Long.compare(b.ts, a.ts);
            }
        });
        return out;
    }

    // ===== Webhooks =====
    private static SharedPreferences prefsS(Context ctx) {
        return ctx.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE);
    }
    private static SharedPreferences prefsR(Context ctx) {
        return ctx.getSharedPreferences(RESULTS_PREFS, Context.MODE_PRIVATE);
    }

    public static void setWebhooks(Context ctx, String incoming, String output, String deleteUrl) {
        if (TextUtils.isEmpty(incoming)) incoming = DEFAULT_INCOMING_WEBHOOK;
        if (TextUtils.isEmpty(deleteUrl)) deleteUrl = DEFAULT_DELETE_WEBHOOK;
        prefsS(ctx).edit()
                .putString(KEY_INCOMING_WEBHOOK, incoming.trim())
                .putString(KEY_OUTPUT_WEBHOOK,   output == null ? "" : output.trim())
                .putString(KEY_DELETE_WEBHOOK,   deleteUrl.trim())
                .apply();
    }

    public static String getIncomingWebhook(Context ctx) {
        String s = prefsS(ctx).getString(KEY_INCOMING_WEBHOOK, DEFAULT_INCOMING_WEBHOOK);
        return TextUtils.isEmpty(s) ? DEFAULT_INCOMING_WEBHOOK : s;
    }
    /** Output = ה-upload */
    public static String getUploadWebhook(Context ctx) {
        return prefsS(ctx).getString(KEY_OUTPUT_WEBHOOK, "");
    }
    public static String getDeleteWebhook(Context ctx) {
        String s = prefsS(ctx).getString(KEY_DELETE_WEBHOOK, DEFAULT_DELETE_WEBHOOK);
        return TextUtils.isEmpty(s) ? DEFAULT_DELETE_WEBHOOK : s;
    }
}
