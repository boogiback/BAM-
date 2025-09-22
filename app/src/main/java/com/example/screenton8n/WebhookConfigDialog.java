package com.example.screenton8n;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

public class WebhookConfigDialog extends DialogFragment {

    interface Listener {
        void onWebhooksSaved(String uploadUrl, String deleteUrl);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View v = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_configure_webhooks, null, false);

        EditText etIncoming = v.findViewById(R.id.etIncoming);
        EditText etOutput   = v.findViewById(R.id.etOutput);
        EditText etDelete   = v.findViewById(R.id.etDelete);
        Button btnSave      = v.findViewById(R.id.btnSaveWebhooks);

        // Prefill from store
        etIncoming.setText(LeadStore.getIncomingWebhook(requireContext()));
        etOutput.setText(LeadStore.getUploadWebhook(requireContext()));
        etDelete.setText(LeadStore.getDeleteWebhook(requireContext()));

        btnSave.setOnClickListener(view -> {
            LeadStore.setWebhooks(
                    requireContext(),
                    etIncoming.getText().toString().trim(),
                    etOutput.getText().toString().trim(),
                    etDelete.getText().toString().trim()
            );

            // Java 11-compatible instanceof + cast
            if (getActivity() instanceof Listener) {
                Listener l = (Listener) getActivity();
                l.onWebhooksSaved(etOutput.getText().toString(), etDelete.getText().toString());
            }
            dismiss();
        });

        return new AlertDialog.Builder(requireContext())
                .setView(v)
                .create();
    }
}
