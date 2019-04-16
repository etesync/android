package com.etesync.syncadapter.ui

import android.accounts.Account
import android.app.Dialog
import android.app.ProgressDialog
import android.content.Context
import android.os.AsyncTask
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.etesync.syncadapter.*
import com.etesync.syncadapter.journalmanager.Crypto
import com.etesync.syncadapter.journalmanager.JournalManager
import com.etesync.syncadapter.journalmanager.UserInfoManager
import com.etesync.syncadapter.model.CollectionInfo
import com.etesync.syncadapter.model.JournalEntity
import okhttp3.HttpUrl

class AddMemberFragment : DialogFragment() {
    private lateinit var account: Account
    private var settings: AccountSettings? = null
    private var ctx: Context? = null
    private var remote: HttpUrl? = null
    private lateinit var info: CollectionInfo
    private lateinit var memberEmail: String
    private lateinit var memberPubKey: ByteArray
    private var readOnly: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        account = arguments?.getParcelable(Constants.KEY_ACCOUNT)!!
        info = arguments?.getSerializable(Constants.KEY_COLLECTION_INFO) as CollectionInfo
        memberEmail = arguments?.getString(KEY_MEMBER)!!.toLowerCase()
        readOnly = arguments?.getBoolean(KEY_READ_ONLY)!!
        ctx = context
        try {
            settings = AccountSettings(ctx!!, account)
        } catch (e: InvalidAccountException) {
            e.printStackTrace()
        }

        remote = HttpUrl.get(settings!!.uri!!)

        MemberAdd().execute()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val progress = ProgressDialog(context)
        progress.setTitle(R.string.collection_members_adding)
        progress.setMessage(getString(R.string.please_wait))
        progress.isIndeterminate = true
        progress.setCanceledOnTouchOutside(false)
        isCancelable = false
        return progress
    }

    private inner class MemberAdd : AsyncTask<Void, Void, MemberAdd.AddResult>() {
        override fun doInBackground(vararg voids: Void): AddResult {
            try {
                val httpClient = HttpClient.Builder(ctx, settings).build().okHttpClient
                val userInfoManager = UserInfoManager(httpClient, remote!!)

                val userInfo = userInfoManager.fetch(memberEmail)
                        ?: throw Exception(getString(R.string.collection_members_error_user_not_found, memberEmail))
                memberPubKey = userInfo.pubkey!!
                return AddResult(null)
            } catch (e: Exception) {
                return AddResult(e)
            }

        }

        override fun onPostExecute(result: AddResult) {
            if (result.throwable == null) {
                val fingerprint = Crypto.AsymmetricCryptoManager.getPrettyKeyFingerprint(memberPubKey)
                val view = LayoutInflater.from(context).inflate(R.layout.fingerprint_alertdialog, null)
                (view.findViewById<View>(R.id.body) as TextView).text = getString(R.string.trust_fingerprint_body, memberEmail)
                (view.findViewById<View>(R.id.fingerprint) as TextView).text = fingerprint
                AlertDialog.Builder(activity!!)
                        .setIcon(R.drawable.ic_fingerprint_dark)
                        .setTitle(R.string.trust_fingerprint_title)
                        .setView(view)
                        .setPositiveButton(android.R.string.ok) { _, _ -> MemberAddSecond().execute() }
                        .setNegativeButton(android.R.string.cancel) { _, _ -> dismiss() }.show()
            } else {
                AlertDialog.Builder(activity!!)
                        .setIcon(R.drawable.ic_error_dark)
                        .setTitle(R.string.collection_members_add_error)
                        .setMessage(result.throwable.message)
                        .setPositiveButton(android.R.string.yes) { _, _ -> }.show()
                dismiss()
            }
        }

        internal inner class AddResult(val throwable: Throwable?)
    }

    private inner class MemberAddSecond : AsyncTask<Void, Void, MemberAddSecond.AddResultSecond>() {
        override fun doInBackground(vararg voids: Void): AddResultSecond {
            try {
                val settings = settings!!
                val httpClient = HttpClient.Builder(ctx!!, settings).build().okHttpClient
                val journalsManager = JournalManager(httpClient, remote!!)

                val data = (ctx!!.applicationContext as App).data
                val journalEntity = JournalEntity.fetchOrCreate(data, info)

                val crypto: Crypto.CryptoManager
                if (journalEntity.encryptedKey != null) {
                    crypto = Crypto.CryptoManager(info.version, settings.keyPair!!, journalEntity.encryptedKey)
                } else {
                    crypto = Crypto.CryptoManager(info.version, settings.password(), info.uid!!)
                }
                val journal = JournalManager.Journal.fakeWithUid(info.uid!!)

                val encryptedKey = crypto.getEncryptedKey(settings.keyPair!!, memberPubKey)
                val member = JournalManager.Member(memberEmail, encryptedKey!!, readOnly)
                journalsManager.addMember(journal, member)
                return AddResultSecond(null)
            } catch (e: Exception) {
                return AddResultSecond(e)
            }

        }

        override fun onPostExecute(result: AddResultSecond) {
            if (result.throwable == null) {
                (activity as Refreshable).refresh()
            } else {
                AlertDialog.Builder(activity!!)
                        .setIcon(R.drawable.ic_error_dark)
                        .setTitle(R.string.collection_members_add_error)
                        .setMessage(result.throwable.message)
                        .setPositiveButton(android.R.string.yes) { _, _ -> }.show()
            }
            dismiss()
        }

        internal inner class AddResultSecond(val throwable: Throwable?)
    }

    companion object {
        private val KEY_MEMBER = "memberEmail"
        private val KEY_READ_ONLY = "readOnly"

        fun newInstance(account: Account, info: CollectionInfo, email: String, readOnly: Boolean): AddMemberFragment {
            val frag = AddMemberFragment()
            val args = Bundle(1)
            args.putParcelable(Constants.KEY_ACCOUNT, account)
            args.putSerializable(Constants.KEY_COLLECTION_INFO, info)
            args.putString(KEY_MEMBER, email)
            args.putBoolean(KEY_READ_ONLY, readOnly)
            frag.arguments = args
            return frag
        }
    }
}
