/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter.ui

import android.accounts.Account
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.etesync.syncadapter.AccountSettings
import com.etesync.syncadapter.HttpClient
import com.etesync.syncadapter.R
import com.etesync.syncadapter.journalmanager.Crypto
import com.etesync.syncadapter.journalmanager.JournalManager
import com.etesync.syncadapter.journalmanager.UserInfoManager
import com.etesync.syncadapter.log.Logger
import com.etesync.syncadapter.syncadapter.requestSync
import com.google.android.material.textfield.TextInputLayout
import okhttp3.HttpUrl
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import java.util.*

open class ChangeEncryptionPasswordActivity : BaseActivity() {

    protected lateinit var account: Account
    lateinit var progress: ProgressDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        account = intent.extras!!.getParcelable(EXTRA_ACCOUNT)!!

        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        setContentView(R.layout.change_encryption_password)
    }

    fun onCancelClicked(v: View) {
        finish()
    }

    fun changePasswordError(e: Exception) {
        progress.dismiss()
        AlertDialog.Builder(this)
                .setTitle(R.string.wrong_encryption_password)
                .setIcon(R.drawable.ic_error_dark)
                .setMessage(getString(R.string.wrong_encryption_password_content, e.localizedMessage))
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    // dismiss
                }.show()
    }

    fun changePasswordDo(old_password: String, new_password: String) {
        val settings = AccountSettings(this, account)

        doAsync {
            val httpClient = HttpClient.Builder(this@ChangeEncryptionPasswordActivity, settings).setForeground(false).build().okHttpClient

            Logger.log.info("Started deriving old key")
            val old_key = Crypto.deriveKey(account.name, old_password)
            Logger.log.info("Finished deriving old key")

            var cryptoManager: Crypto.CryptoManager
            val principal = HttpUrl.get(settings.uri!!)!!

            try {
                val userInfoManager = UserInfoManager(httpClient, principal)
                val userInfo = userInfoManager.fetch(account.name)!!
                Logger.log.info("Fetched userInfo for " + account.name)
                cryptoManager = Crypto.CryptoManager(userInfo.version!!.toInt(), old_key, "userInfo")
                userInfo.verify(cryptoManager)

                Logger.log.info("Started deriving new key")
                val new_key = Crypto.deriveKey(account.name, new_password)
                Logger.log.info("Finished deriving new key")

                val userInfoContent = userInfo.getContent(cryptoManager)!!
                cryptoManager = Crypto.CryptoManager(userInfo.version.toInt(), new_key, "userInfo")
                userInfo.setContent(cryptoManager, userInfoContent)

                Logger.log.info("Fetching journal list")
                val membersToAdd = LinkedList<Pair<JournalManager.Journal, ByteArray?>>()
                val journalManager = JournalManager(httpClient, principal)
                val journals = journalManager.list()
                for (journal in journals) {
                    if (journal.owner != account.name) {
                        continue
                    }

                    if (journal.key != null) {
                        // We don't need to handle those cases, as they are already encrypted using pubkey
                        continue
                    } else {
                        cryptoManager = Crypto.CryptoManager(journal.version, old_key, journal.uid!!)
                    }

                    Logger.log.info("Converting journal ${journal.uid}")
                    journal.verify(cryptoManager)

                    membersToAdd.add(Pair(journal, cryptoManager.getEncryptedKey(settings.keyPair!!, userInfo.pubkey!!)))
                }

                Logger.log.info("Finished converting account. Uploading changes")
                userInfoManager.update(userInfo)

                for ((journal, encryptedKey) in membersToAdd) {
                    if (journal.owner != account.name) {
                        continue
                    }

                    Logger.log.info("Uploading journal ${journal.uid}")
                    val member = JournalManager.Member(account.name, encryptedKey!!)
                    journalManager.addMember(journal, member)
                }

                settings.password(new_key)
                Logger.log.info("Finished uploading changes. Encryption password changed successfully.")

                uiThread {
                    progress.dismiss()
                    AlertDialog.Builder(this@ChangeEncryptionPasswordActivity)
                            .setTitle(R.string.change_encryption_password_success_title)
                            .setMessage(R.string.change_encryption_password_success_body)
                            .setPositiveButton(android.R.string.ok) { _, _ ->
                                this@ChangeEncryptionPasswordActivity.finish()
                            }.show()

                    requestSync(account)
                }
            } catch (e: Exception) {
                uiThread {
                    changePasswordError(e)
                }
                return@doAsync
            }
        }
    }

    fun changePasswordClicked(v: View) {
        val old_password_view = findViewById<TextInputLayout>(R.id.encryption_password)
        val new_password_view = findViewById<TextInputLayout>(R.id.new_encryption_password)

        var valid = true
        val old_password = old_password_view.editText?.text.toString()
        if (old_password.isEmpty()) {
            old_password_view.error = getString(R.string.login_password_required)
            valid = false
        } else {
            old_password_view.error = null
        }
        val new_password = new_password_view.editText?.text.toString()
        if (new_password.isEmpty()) {
            new_password_view.error = getString(R.string.login_password_required)
            valid = false
        } else {
            new_password_view.error = null
        }

        if (!valid) {
            return
        }

        AlertDialog.Builder(this)
                .setTitle(R.string.delete_collection_confirm_title)
                .setMessage(R.string.change_encryption_password_are_you_sure)
                .setPositiveButton(android.R.string.yes) { _, _ ->
                    changePasswordDo(old_password, new_password)
                    progress = ProgressDialog(this)
                    progress.setTitle(R.string.login_encryption_setup_title)
                    progress.setMessage(getString(R.string.login_encryption_setup))
                    progress.isIndeterminate = true
                    progress.setCanceledOnTouchOutside(false)
                    progress.setCancelable(false)
                    progress.show()
                }
                .setNegativeButton(android.R.string.no) { _, _ -> }
                .create().show()
    }

    companion object {
        internal val EXTRA_ACCOUNT = "account"

        fun newIntent(context: Context, account: Account): Intent {
            val intent = Intent(context, ChangeEncryptionPasswordActivity::class.java)
            intent.putExtra(CreateCollectionActivity.EXTRA_ACCOUNT, account)
            return intent
        }
    }
}
