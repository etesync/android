/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter.ui.etebase

import android.app.Dialog
import android.app.ProgressDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckedTextView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.observe
import com.etebase.client.Account
import com.etebase.client.Client
import com.etebase.client.User
import com.etebase.client.exceptions.EtebaseException
import com.etesync.syncadapter.Constants
import com.etesync.syncadapter.HttpClient
import com.etesync.syncadapter.R
import com.etesync.syncadapter.ui.WebViewActivity
import com.etesync.syncadapter.ui.setup.*
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import net.cachapa.expandablelayout.ExpandableLayout
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import java.net.URI
import java.util.concurrent.Future

class SignupFragment(private val initialUsername: String?, private val initialPassword: String?) : Fragment() {
    internal lateinit var editUserName: TextInputLayout
    internal lateinit var editEmail: TextInputLayout
    internal lateinit var editPassword: TextInputLayout

    internal lateinit var showAdvanced: CheckedTextView
    internal lateinit var customServer: TextInputEditText


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val v = inflater.inflate(R.layout.signup_fragment, container, false)

        editUserName = v.findViewById(R.id.user_name)
        editEmail = v.findViewById(R.id.email)
        editPassword = v.findViewById(R.id.url_password)
        showAdvanced = v.findViewById(R.id.show_advanced)
        customServer = v.findViewById(R.id.custom_server)
        v.findViewById<TextView>(R.id.trial_notice).setOnClickListener {
            WebViewActivity.openUrl(requireContext(), Constants.pricing)
        }

        if (savedInstanceState == null) {
            editUserName.editText?.setText(initialUsername ?: "")
            editPassword.editText?.setText(initialPassword ?: "")
        }

        val login = v.findViewById<Button>(R.id.login)
        login.setOnClickListener {
            parentFragmentManager.commit {
                replace(android.R.id.content, LoginCredentialsFragment(editUserName.editText?.text.toString(), editPassword.editText?.text.toString()))
            }
        }

        val createAccount = v.findViewById<Button>(R.id.create_account)
        createAccount.setOnClickListener {
            val credentials = validateData()
            if (credentials != null) {
                SignupDoFragment(credentials).show(requireFragmentManager(), null)
            }
        }

        val advancedLayout = v.findViewById<View>(R.id.advanced_layout) as ExpandableLayout

        showAdvanced.setOnClickListener {
            if (showAdvanced.isChecked) {
                showAdvanced.isChecked = false
                advancedLayout.collapse()
            } else {
                showAdvanced.isChecked = true
                advancedLayout.expand()
            }
        }

        return v
    }

    protected fun validateData(): SignupCredentials? {
        var valid = true

        val userName = editUserName.editText?.text.toString()
        // FIXME: this validation should only be done in the server, we are doing it here until the Java library supports field errors
        if ((userName.length < 6) || (!userName.matches(Regex("""^[\w.-]+$""")))) {
            editUserName.error = getString(R.string.login_username_error)
            valid = false
        } else {
            editUserName.error = null
        }

        val email = editEmail.editText?.text.toString()
        if (email.isEmpty()) {
            editEmail.error = getString(R.string.login_email_address_error)
            valid = false
        } else {
            editEmail.error = null
        }

        val password = editPassword.editText?.text.toString()
        if (password.length < 8) {
            editPassword.error = getString(R.string.signup_password_restrictions)
            valid = false
        } else {
            editPassword.error = null
        }

        var uri: URI? = null
        if (showAdvanced.isChecked) {
            val server = customServer.text.toString()
            // If this field is null, just use the default
            if (!server.isEmpty()) {
                val url = server.toHttpUrlOrNull()
                if (url != null) {
                    uri = url.toUri()
                    customServer.error = null
                } else {
                    customServer.error = getString(R.string.login_custom_server_error)
                    valid = false
                }
            }
        }

        return if (valid) SignupCredentials(uri, userName, email, password) else null
    }
}



class SignupDoFragment(private val signupCredentials: SignupCredentials) : DialogFragment() {
    private val model: ConfigurationViewModel by viewModels()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val progress = ProgressDialog(activity)
        progress.setTitle(R.string.setting_up_encryption)
        progress.setMessage(getString(R.string.setting_up_encryption_content))
        progress.isIndeterminate = true
        progress.setCanceledOnTouchOutside(false)
        isCancelable = false
        return progress
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            model.signup(requireContext(), signupCredentials)
            model.observe(this) {
                if (it.isFailed) {
                    // no service found: show error message
                    requireFragmentManager().beginTransaction()
                            .add(DetectConfigurationFragment.NothingDetectedFragment.newInstance(it.error!!.localizedMessage), null)
                            .commitAllowingStateLoss()
                } else {
                    requireFragmentManager().beginTransaction()
                            .replace(android.R.id.content, CreateAccountFragment.newInstance(it))
                            .addToBackStack(null)
                            .commitAllowingStateLoss()
                }
                dismissAllowingStateLoss()
            }
        }
    }
}

class ConfigurationViewModel : ViewModel() {
    val account = MutableLiveData<BaseConfigurationFinder.Configuration>()
    private var asyncTask: Future<Unit>? = null

    fun signup(context: Context, credentials: SignupCredentials) {
        asyncTask = doAsync {
            val httpClient = HttpClient.Builder(context).build().okHttpClient
            val uri = credentials.uri ?: URI(Constants.etebaseServiceUrl)
            var etebaseSession: String? = null
            var exception: Throwable? = null
            try {
                val client = Client.create(httpClient, uri.toString())
                val user = User(credentials.userName, credentials.email)
                val etebase = Account.signup(client, user, credentials.password)
                etebaseSession = etebase.save(null)
            } catch (e: EtebaseException) {
                exception = e
            }

            uiThread {
                account.value = BaseConfigurationFinder.Configuration(
                        uri,
                        credentials.userName,
                        etebaseSession,
                        null,
                        null,
                        exception
                )
            }
        }
    }

    // We just need it for the migration - maybe merge it with login later on
    fun login(context: Context, credentials: LoginCredentials) {
        asyncTask = doAsync {
            val httpClient = HttpClient.Builder(context).build().okHttpClient
            val uri = credentials.uri ?: URI(Constants.etebaseServiceUrl)
            var etebaseSession: String? = null
            var exception: Throwable? = null
            try {
                val client = Client.create(httpClient, uri.toString())
                val etebase = Account.login(client, credentials.userName, credentials.password)
                etebaseSession = etebase.save(null)
            } catch (e: EtebaseException) {
                exception = e
            }

            uiThread {
                account.value = BaseConfigurationFinder.Configuration(
                        uri,
                        credentials.userName,
                        etebaseSession,
                        null,
                        null,
                        exception
                )
            }
        }
    }

    fun cancelLoad() {
        asyncTask?.cancel(true)
    }

    fun observe(owner: LifecycleOwner, observer: (BaseConfigurationFinder.Configuration) -> Unit) =
            account.observe(owner, observer)
}

data class SignupCredentials(val uri: URI?, val userName: String, val email: String, val password: String)