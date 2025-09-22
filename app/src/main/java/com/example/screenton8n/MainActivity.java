package com.example.screenton8n;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity
        implements WebhookConfigDialog.Listener, LeadAdapter.Callbacks {

    private RecyclerView rv;
    private LeadAdapter adapter;
    private ArrayList<Lead> leads;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar tb = findViewById(R.id.toolbar);
        setSupportActionBar(tb);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayShowTitleEnabled(false);
        tb.setNavigationIcon(R.drawable.ic_menu);
        tb.setNavigationOnClickListener(v -> showConfigureWebhooksDialog());

        rv = findViewById(R.id.recycler);
        rv.setLayoutManager(new LinearLayoutManager(this));

        leads = LeadStore.loadLeadsCompat(this);
        adapter = new LeadAdapter(this, leads, this);
        rv.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        leads = LeadStore.loadLeadsCompat(this);
        adapter = new LeadAdapter(this, leads, this);
        rv.setAdapter(adapter);
    }

    private void showConfigureWebhooksDialog() {
        new WebhookConfigDialog().show(getSupportFragmentManager(), "cfg");
    }

    // ===== callbacks =====
    @Override public void onWebhooksSaved(String uploadUrl, String deleteUrl) {
        Utils.toast(this, getString(R.string.webhooks_saved));
    }
    @Override public void onDatasetChanged() { LeadStore.saveLeads(this, leads); }
    @Override public String getUploadWebhook() { return LeadStore.getUploadWebhook(this); }
    @Override public String getDeleteWebhook() { return LeadStore.getDeleteWebhook(this); }
}
