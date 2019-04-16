package com.etesync.syncadapter.ui.importlocal

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.etesync.syncadapter.R
import java.io.Serializable

/**
 * Created by tal on 30/03/17.
 */

class ResultFragment : DialogFragment() {
    private var result: ImportResult? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        result = arguments!!.getSerializable(KEY_RESULT) as ImportResult
    }

    override fun onDismiss(dialog: DialogInterface?) {
        super.onDismiss(dialog)
        val activity = activity
        if (activity is DialogInterface) {
            (activity as DialogInterface).dismiss()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val icon: Int
        val title: Int
        val msg: String
        if (result!!.isFailed) {
            icon = R.drawable.ic_error_dark
            title = R.string.import_dialog_failed_title
            msg = result!!.e!!.localizedMessage
        } else {
            icon = R.drawable.ic_import_export_black
            title = R.string.import_dialog_title
            msg = getString(R.string.import_dialog_success, result!!.total, result!!.added, result!!.updated, result!!.skipped)
        }
        return AlertDialog.Builder(activity!!)
                .setTitle(title)
                .setIcon(icon)
                .setMessage(msg)
                .setPositiveButton(android.R.string.ok) { dialog, which ->
                    // dismiss
                }
                .create()
    }

    class ImportResult : Serializable {
        var total: Long = 0
        var added: Long = 0
        var updated: Long = 0
        var e: Exception? = null

        val isFailed: Boolean
            get() = e != null

        val skipped: Long
            get() = total - (added + updated)

        override fun toString(): String {
            return "ResultFragment.ImportResult(total=" + this.total + ", added=" + this.added + ", updated=" + this.updated + ", e=" + this.e + ")"
        }
    }

    interface OnImportCallback {
        fun onImportResult(importResult: ImportResult)
    }

    companion object {
        private val KEY_RESULT = "result"

        fun newInstance(result: ImportResult): ResultFragment {
            val args = Bundle()
            args.putSerializable(KEY_RESULT, result)
            val fragment = ResultFragment()
            fragment.arguments = args
            return fragment
        }
    }
}
