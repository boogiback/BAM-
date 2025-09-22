package com.example.screenton8n;

import org.json.JSONException;
import org.json.JSONObject;

public class Lead {
    public String id;
    public String jobId;
    public long   ts;
    public String firstName;
    public String lastName;
    public String phone;
    public String email;
    public String type;
    public String notes;
    public String summary;
    public boolean uploaded;

    public static Lead fromJson(JSONObject j) {
        Lead l = new Lead();
        l.id        = j.optString("id", "");
        l.jobId     = j.optString("jobId", "");
        l.ts        = j.optLong("ts", System.currentTimeMillis());
        l.summary   = j.optString("summary", "");
        l.uploaded  = j.optBoolean("uploaded", false);

        JSONObject r = j.optJSONObject("result");
        if (r == null) r = new JSONObject();
        l.firstName = r.optString("first_name", "");
        l.lastName  = r.optString("last_name", "");
        l.phone     = r.optString("phone", "");
        l.email     = r.optString("email", "");
        l.type      = r.optString("type", "");
        l.notes     = r.optString("notes", "");
        return l;
    }

    public JSONObject toJson() {
        JSONObject j = new JSONObject();
        try {
            j.put("id", id);
            j.put("jobId", jobId);
            j.put("ts", ts);
            j.put("summary", summary);
            j.put("uploaded", uploaded);
            JSONObject r = new JSONObject();
            r.put("first_name", firstName);
            r.put("last_name",  lastName);
            r.put("phone",      phone);
            r.put("email",      email);
            r.put("type",       type);
            r.put("notes",      notes);
            j.put("result", r);
        } catch (JSONException ignored) {}
        return j;
    }

    public String fullName() {
        String f = firstName == null ? "" : firstName.trim();
        String l = lastName  == null ? "" : lastName.trim();
        String s = (f + " " + l).trim();
        return s.isEmpty() ? "(No name)" : s;
    }
}
