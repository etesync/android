package com.etesync.syncadapter.ui.importlocal;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;

import com.etesync.syncadapter.R;

import java.io.Serializable;

/**
 * Created by tal on 30/03/17.
 */

public class ResultFragment extends DialogFragment {
    private static final String KEY_RESULT = "result";
    private ImportResult result;

    public static ResultFragment newInstance(ImportResult result) {
        Bundle args = new Bundle();
        args.putSerializable(KEY_RESULT, result);
        ResultFragment fragment = new ResultFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        result = (ImportResult) getArguments().getSerializable(KEY_RESULT);
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        Activity activity = getActivity();
        if (activity instanceof DialogInterface) {
            ((DialogInterface)activity).dismiss();
        }
    }

    @Override
    @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        int icon;
        int title;
        String msg;
        if (result.isFailed()) {
            icon = R.drawable.ic_error_dark;
            title = R.string.import_dialog_failed_title;
            msg = result.e.getLocalizedMessage();
        } else {
            icon = R.drawable.ic_import_export_black;
            title = R.string.import_dialog_title;
            msg = getString(R.string.import_dialog_success, result.total, result.added, result.updated, result.getSkipped());
        }
        return new AlertDialog.Builder(getActivity())
                .setTitle(title)
                .setIcon(icon)
                .setMessage(msg)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // dismiss
                    }
                })
                .create();
    }

    public static class ImportResult implements Serializable {
        public long total;
        public long added;
        public long updated;
        public Exception e;

        public boolean isFailed() {
            return (e != null);
        }

        public long getSkipped() {
            return total - (added + updated);
        }

        @java.lang.Override
        @java.lang.SuppressWarnings("all")
        public java.lang.String toString() {
            return "ResultFragment.ImportResult(total=" + this.total + ", added=" + this.added + ", updated=" + this.updated + ", e=" + this.e + ")";
        }
    }

    public interface OnImportCallback {
        void onImportResult(ImportResult importResult);
    }
}
