package com.etesync.syncadapter.ui

import android.accounts.Account
import android.app.Dialog
import android.app.ProgressDialog
import android.os.AsyncTask
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.etesync.syncadapter.*
import com.etesync.syncadapter.journalmanager.JournalManager
import com.etesync.syncadapter.model.CollectionInfo
import okhttp3.HttpUrl

class RemoveMemberFragment : DialogFragment() {
    private var settings: AccountSettings? = null
    private var info: CollectionInfo? = null
    private var memberEmail: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val account = arguments!!.getParcelable<Account>(Constants.KEY_ACCOUNT)
        info = arguments!!.getSerializable(Constants.KEY_COLLECTION_INFO) as CollectionInfo
        memberEmail = arguments!!.getString(KEY_MEMBER)
        try {
            settings = AccountSettings(context!!, account!!)
        } catch (e: InvalidAccountException) {
            e.printStackTrace()
        }

        MemberRemove().execute()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val progress = ProgressDialog(context)
        progress.setTitle(R.string.collection_members_removing)
        progress.setMessage(getString(R.string.please_wait))
        progress.isIndeterminate = true
        progress.setCanceledOnTouchOutside(false)
        isCancelable = false
        return progress
    }

    private inner class MemberRemove : AsyncTask<Void, Void, MemberRemove.RemoveResult>() {
        override fun doInBackground(vararg voids: Void): RemoveResult {
            val httpClient = HttpClient.Builder(context, settings).build()

            try {
                val remote = HttpUrl.get(settings!!.uri!!)

                val journalsManager = JournalManager(httpClient.okHttpClient, remote!!)
                val journal = JournalManager.Journal.fakeWithUid(info!!.uid!!)

                val member = JournalManager.Member(memberEmail!!, "placeholder".toByteArray())
                journalsManager.deleteMember(journal, member)

                return RemoveResult(null)
            } catch (e: Exception) {
                return RemoveResult(e)
            } finally {
                httpClient.close()
            }
        }

        override fun onPostExecute(result: RemoveResult) {
            if (result.throwable == null) {
                (activity as Refreshable).refresh()
            } else {
                AlertDialog.Builder(activity!!)
                        .setIcon(R.drawable.ic_error_dark)
                        .setTitle(R.string.collection_members_remove_error)
                        .setMessage(result.throwable.message)
                        .setPositiveButton(android.R.string.yes) { _, _ -> }.show()
            }
            dismiss()
        }

        internal inner class RemoveResult(val throwable: Throwable?)
    }

    companion object {
        private val KEY_MEMBER = "memberEmail"

        fun newInstance(account: Account, info: CollectionInfo, email: String): RemoveMemberFragment {
            val frag = RemoveMemberFragment()
            val args = Bundle(1)
            args.putParcelable(Constants.KEY_ACCOUNT, account)
            args.putSerializable(Constants.KEY_COLLECTION_INFO, info)
            args.putString(KEY_MEMBER, email)
            frag.arguments = args
            return frag
        }
    }
}
