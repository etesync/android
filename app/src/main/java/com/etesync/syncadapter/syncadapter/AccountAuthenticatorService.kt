/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package com.etesync.syncadapter.syncadapter

import android.accounts.*
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import com.etesync.syncadapter.ui.setup.LoginActivity

class AccountAuthenticatorService : Service() {

    private var accountAuthenticator: AccountAuthenticator? = null

    override fun onCreate() {
        accountAuthenticator = AccountAuthenticator(this)
    }

    override fun onBind(intent: Intent): IBinder? {
        return if (intent.action == android.accounts.AccountManager.ACTION_AUTHENTICATOR_INTENT) accountAuthenticator!!.iBinder else null
    }


    private class AccountAuthenticator(internal val context: Context) : AbstractAccountAuthenticator(context) {

        @Throws(NetworkErrorException::class)
        override fun addAccount(response: AccountAuthenticatorResponse, accountType: String, authTokenType: String,
                                requiredFeatures: Array<String>, options: Bundle): Bundle {
            val intent = Intent(context, LoginActivity::class.java)
            intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response)
            val bundle = Bundle()
            bundle.putParcelable(AccountManager.KEY_INTENT, intent)
            return bundle
        }

        @Throws(NetworkErrorException::class)
        override fun confirmCredentials(response: AccountAuthenticatorResponse, account: Account, options: Bundle): Bundle? {
            return null
        }

        override fun editProperties(response: AccountAuthenticatorResponse, accountType: String): Bundle? {
            return null
        }

        @Throws(NetworkErrorException::class)
        override fun getAuthToken(response: AccountAuthenticatorResponse, account: Account, authTokenType: String, options: Bundle): Bundle? {
            return null
        }

        override fun getAuthTokenLabel(authTokenType: String): String? {
            return null
        }

        @Throws(NetworkErrorException::class)
        override fun hasFeatures(response: AccountAuthenticatorResponse, account: Account, features: Array<String>): Bundle? {
            return null
        }

        @Throws(NetworkErrorException::class)
        override fun updateCredentials(response: AccountAuthenticatorResponse, account: Account, authTokenType: String, options: Bundle): Bundle? {
            return null
        }

    }
}
