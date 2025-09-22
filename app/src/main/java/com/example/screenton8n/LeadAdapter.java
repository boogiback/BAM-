package com.example.screenton8n;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executors;

public class LeadAdapter extends RecyclerView.Adapter<LeadAdapter.VH> {

    public interface Callbacks {
        void onDatasetChanged();
        String getUploadWebhook();
        String getDeleteWebhook();
    }

    private final Context ctx;
    private final ArrayList<Lead> data;
    private final Callbacks callbacks;

    public LeadAdapter(Context ctx, ArrayList<Lead> data, Callbacks callbacks) {
        this.ctx = ctx;
        this.data = data;
        this.callbacks = callbacks;
    }

    static class VH extends RecyclerView.ViewHolder {
        View card;
        LinearLayout contentViewMode, contentEditMode;
        TextView tvTitle, tvTime, tvSummary, tvPhone, tvEmail, tvType, tvNotes;
        EditText etFirst, etLast, etType, etNotes;
        Button btnUpload, btnDelete, btnEdit, btnSave, btnCancel;

        VH(@NonNull View v) {
            super(v);
            card = v.findViewById(R.id.cardRoot);
            contentViewMode = v.findViewById(R.id.contentViewMode);
            contentEditMode = v.findViewById(R.id.contentEditMode);
            tvTitle = v.findViewById(R.id.tvName);
            tvTime  = v.findViewById(R.id.tvTs);
            tvSummary = v.findViewById(R.id.tvSummary);
            tvPhone = v.findViewById(R.id.tvPhone);
            tvEmail = v.findViewById(R.id.tvEmail);
            tvType = v.findViewById(R.id.tvType);
            tvNotes = v.findViewById(R.id.tvNotes);
            etFirst = v.findViewById(R.id.etFirstName);
            etLast  = v.findViewById(R.id.etLastName);
            etType  = v.findViewById(R.id.etType);
            etNotes = v.findViewById(R.id.etNotes);
            btnUpload = v.findViewById(R.id.btnUpload);
            btnDelete = v.findViewById(R.id.btnDelete);
            btnEdit   = v.findViewById(R.id.btnEdit);
            btnSave   = v.findViewById(R.id.btnSave);
            btnCancel = v.findViewById(R.id.btnCancel);
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_lead_card, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Lead L = data.get(pos);

        String tsStr = DateFormat.getDateTimeInstance(
                        DateFormat.MEDIUM, DateFormat.MEDIUM, Locale.getDefault())
                .format(new Date(L.ts));
        h.tvTime.setText(tsStr);

        // View mode
        h.tvTitle.setText(L.fullName());
        h.tvSummary.setText(TextUtils.isEmpty(L.summary) ? "" : L.summary);
        h.tvPhone.setText(TextUtils.isEmpty(L.phone) ? "-" : L.phone);
        h.tvEmail.setText(TextUtils.isEmpty(L.email) ? "-" : L.email);
        h.tvType.setText(TextUtils.isEmpty(L.type) ? "-" : L.type);
        h.tvNotes.setText(TextUtils.isEmpty(L.notes) ? "-" : L.notes);

        // Edit mode fields
        h.etFirst.setText(L.firstName);
        h.etLast.setText(L.lastName);
        h.etType.setText(L.type);
        h.etNotes.setText(L.notes);

        // background by upload state
        h.card.setBackgroundColor(L.uploaded
                ? ContextCompat.getColor(ctx, R.color.lead_uploaded_bg)
                : ContextCompat.getColor(ctx, R.color.card_bg));

        setMode(h, false);

        // links
        h.tvPhone.setOnClickListener(v -> openWhatsApp(L.phone));
        h.tvEmail.setOnClickListener(v -> openEmail(L.email));

        // buttons
        h.btnEdit.setOnClickListener(v -> setMode(h, true));
        h.btnCancel.setOnClickListener(v -> {
            h.etFirst.setText(L.firstName);
            h.etLast.setText(L.lastName);
            h.etType.setText(L.type);
            h.etNotes.setText(L.notes);
            setMode(h, false);
        });
        h.btnSave.setOnClickListener(v -> {
            L.firstName = h.etFirst.getText().toString().trim();
            L.lastName  = h.etLast.getText().toString().trim();
            L.type      = h.etType.getText().toString().trim();
            L.notes     = h.etNotes.getText().toString().trim();
            notifyItemChanged(h.getAdapterPosition());
            if (callbacks != null) callbacks.onDatasetChanged();
            setMode(h, false);
        });
        h.btnUpload.setOnClickListener(v -> doPostWebhook(L, h));
        h.btnDelete.setOnClickListener(v -> doDeleteWebhookAndRemove(L, h));
    }

    private void setMode(VH h, boolean edit) {
        h.contentViewMode.setVisibility(edit ? View.GONE : View.VISIBLE);
        h.contentEditMode.setVisibility(edit ? View.VISIBLE : View.GONE);
    }

    private void openWhatsApp(String phone) {
        if (TextUtils.isEmpty(phone)) return;
        Uri uri = Uri.parse("https://wa.me/" + phone.replace("+", "").replace(" ", ""));
        ctx.startActivity(new Intent(Intent.ACTION_VIEW, uri));
    }

    private void openEmail(String email) {
        if (TextUtils.isEmpty(email)) return;
        ctx.startActivity(new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + email)));
    }

    private void doPostWebhook(Lead L, VH h) {
        String url = callbacks == null ? "" : callbacks.getUploadWebhook();
        if (TextUtils.isEmpty(url)) {
            Utils.toast(ctx, ctx.getString(R.string.upload_webhook_missing));
            return;
        }
        Executors.newSingleThreadExecutor().execute(() -> {
            final boolean ok = httpPostJson(url, L.toJson()); // effectively final
            new Handler(Looper.getMainLooper()).post(() -> {
                if (ok) {
                    L.uploaded = true;
                    notifyItemChanged(h.getAdapterPosition());
                    if (callbacks != null) callbacks.onDatasetChanged();
                    Utils.toast(ctx, ctx.getString(R.string.upload_ok));
                } else {
                    Utils.toast(ctx, ctx.getString(R.string.upload_fail));
                }
            });
        });
    }

    private void doDeleteWebhookAndRemove(Lead L, VH h) {
        String url = callbacks == null ? "" : callbacks.getDeleteWebhook();

        Executors.newSingleThreadExecutor().execute(() -> {
            // חישוב תוצאה סופית כ-final
            final boolean okResult = TextUtils.isEmpty(url) || httpPostJson(url, L.toJson());
            final int pos = h.getAdapterPosition();

            new Handler(Looper.getMainLooper()).post(() -> {
                if (pos >= 0 && pos < data.size()) {
                    data.remove(pos);
                    notifyItemRemoved(pos);
                    if (callbacks != null) callbacks.onDatasetChanged();
                }
                Utils.toast(ctx, okResult ? ctx.getString(R.string.delete_ok)
                        : ctx.getString(R.string.delete_fail));
            });
        });
    }

    private boolean httpPostJson(String urlStr, JSONObject payload) {
        HttpURLConnection conn = null;
        try {
            URL u = new URL(urlStr);
            conn = (HttpURLConnection) u.openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            byte[] body = payload.toString().getBytes("UTF-8");
            conn.getOutputStream().write(body);
            int code = conn.getResponseCode();
            return code >= 200 && code < 300;
        } catch (Exception ignored) {
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    @Override
    public int getItemCount() {
        return data.size();
    }
}
