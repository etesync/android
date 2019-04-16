/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter.ui

import android.accounts.Account
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.etesync.syncadapter.Constants
import com.etesync.syncadapter.R
import com.etesync.syncadapter.journalmanager.Exceptions.HttpException
import java.io.IOException

class ExceptionInfoFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val args = arguments
        val exception = args!!.getSerializable(ARG_EXCEPTION) as Exception
        val account = args.getParcelable<Account>(ARG_ACCOUNT)

        var title = R.string.exception
        if (exception is HttpException)
            title = R.string.exception_httpexception
        else if (exception is IOException)
            title = R.string.exception_ioexception

        val dialog = AlertDialog.Builder(context!!)
                .setIcon(R.drawable.ic_error_dark)
                .setTitle(title)
                .setMessage("${exception.javaClass.canonicalName}\n" + exception.localizedMessage)
                .setNegativeButton(R.string.exception_show_details) { _, _ ->
                    val intent = Intent(context, DebugInfoActivity::class.java)
                    intent.putExtra(DebugInfoActivity.KEY_THROWABLE, exception)
                    if (account != null)
                        intent.putExtra(Constants.KEY_ACCOUNT, account)
                    startActivity(intent)
                }
                .setPositiveButton(android.R.string.ok) { _, _ -> }
                .create()
        isCancelable = false
        return dialog
    }

    companion object {
        protected val ARG_ACCOUNT = "account"
        protected val ARG_EXCEPTION = "exception"

        fun newInstance(exception: Exception, account: Account): ExceptionInfoFragment {
            val frag = ExceptionInfoFragment()
            val args = Bundle(1)
            args.putSerializable(ARG_EXCEPTION, exception)
            args.putParcelable(ARG_ACCOUNT, account)
            frag.arguments = args
            return frag
        }
    }
}
