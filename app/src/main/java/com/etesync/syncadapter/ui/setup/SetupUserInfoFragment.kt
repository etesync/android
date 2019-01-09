package com.etesync.syncadapter.ui.setup

import android.accounts.Account
import android.app.Dialog
import android.app.ProgressDialog
import android.content.Context
import android.os.AsyncTask
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.support.v7.app.AlertDialog
import com.etesync.syncadapter.*
import com.etesync.syncadapter.Constants.KEY_ACCOUNT
import com.etesync.syncadapter.journalmanager.Constants
import com.etesync.syncadapter.journalmanager.Crypto
import com.etesync.syncadapter.journalmanager.UserInfoManager
import okhttp3.HttpUrl

class SetupUserInfoFragment : DialogFragment() {
    private lateinit var account: Account
    private lateinit var settings: AccountSettings

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val progress = ProgressDialog(activity)
        progress.setTitle(R.string.login_encryption_setup_title)
        progress.setMessage(getString(R.string.login_encryption_setup))
        progress.isIndeterminate = true
        progress.setCanceledOnTouchOutside(false)
        isCancelable = false
        return progress
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        account = arguments!!.getParcelable(KEY_ACCOUNT)!!

        try {
            settings = AccountSettings(context!!, account)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        SetupUserInfo().execute(account)
    }

    protected inner class SetupUserInfo : AsyncTask<Account, Int, SetupUserInfo.SetupUserInfoResult>() {
        override fun doInBackground(vararg accounts: Account): SetupUserInfo.SetupUserInfoResult {
            try {
                val cryptoManager: Crypto.CryptoManager
                val httpClient = HttpClient.create(context, settings)

                val userInfoManager = UserInfoManager(httpClient, HttpUrl.get(settings.uri)!!)
                var userInfo: UserInfoManager.UserInfo? = userInfoManager[account.name]

                if (userInfo == null) {
                    App.log.info("Creating userInfo for " + account.name)
                    cryptoManager = Crypto.CryptoManager(Constants.CURRENT_VERSION, settings.password(), "userInfo")
                    userInfo = UserInfoManager.UserInfo.generate(cryptoManager, account.name)
                    userInfoManager.create(userInfo)
                } else {
                    App.log.info("Fetched userInfo for " + account.name)
                    cryptoManager = Crypto.CryptoManager(userInfo.version!!.toInt(), settings.password(), "userInfo")
                    userInfo.verify(cryptoManager)
                }

                val keyPair = Crypto.AsymmetricKeyPair(userInfo.getContent(cryptoManager)!!, userInfo.pubkey!!)

                return SetupUserInfoResult(keyPair, null)
            } catch (e: Exception) {
                e.printStackTrace()
                return SetupUserInfoResult(null, e)
            }

        }

        override fun onPostExecute(result: SetupUserInfoResult) {
            if (result.exception == null) {
                settings.keyPair = result.keyPair
            } else {
                val dialog = AlertDialog.Builder(activity!!)
                        .setTitle(R.string.login_user_info_error_title)
                        .setIcon(R.drawable.ic_error_dark)
                        .setMessage(result.exception.localizedMessage)
                        .setPositiveButton(android.R.string.ok) { dialog, which ->
                            // dismiss
                        }
                        .create()
                dialog.show()
            }

            dismissAllowingStateLoss()
        }

        inner class SetupUserInfoResult(val keyPair: Crypto.AsymmetricKeyPair?, val exception: Exception?)
    }

    companion object {

        fun newInstance(account: Account): SetupUserInfoFragment {
            val frag = SetupUserInfoFragment()
            val args = Bundle(1)
            args.putParcelable(KEY_ACCOUNT, account)
            frag.arguments = args
            return frag
        }

        fun hasUserInfo(context: Context, account: Account): Boolean {
            val settings: AccountSettings
            try {
                settings = AccountSettings(context, account)
            } catch (e: InvalidAccountException) {
                e.printStackTrace()
                return false
            }

            return settings.keyPair != null
        }
    }
}
