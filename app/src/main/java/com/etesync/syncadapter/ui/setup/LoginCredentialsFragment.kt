/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter.ui.setup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckedTextView
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.replace
import com.etesync.syncadapter.Constants
import com.etesync.syncadapter.R
import com.etesync.syncadapter.ui.WebViewActivity
import com.etesync.syncadapter.ui.etebase.SignupFragment
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import net.cachapa.expandablelayout.ExpandableLayout
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URI

class LoginCredentialsFragment : Fragment() {
    internal lateinit var editUserName: EditText
    internal lateinit var editUrlPassword: TextInputLayout

    internal lateinit var showAdvanced: CheckedTextView
    internal lateinit var customServer: EditText

    internal var initialUsername: String? = null
    internal var initialPassword: String? = null


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val v = inflater.inflate(R.layout.login_credentials_fragment, container, false)

        editUserName = v.findViewById<TextInputEditText>(R.id.user_name)
        editUrlPassword = v.findViewById<TextInputLayout>(R.id.url_password)
        showAdvanced = v.findViewById<CheckedTextView>(R.id.show_advanced)
        customServer = v.findViewById<TextInputEditText>(R.id.custom_server)

        if (savedInstanceState == null) {
            editUserName.setText(initialUsername ?: "")
            editUrlPassword.editText?.setText(initialPassword ?: "")
        }

        val createAccount = v.findViewById<View>(R.id.create_account) as Button
        createAccount.setOnClickListener {
            parentFragmentManager.commit {
                replace(android.R.id.content, SignupFragment.newInstance(editUserName.text.toString(), editUrlPassword.editText?.text.toString()))
            }
        }

        val login = v.findViewById<View>(R.id.login) as Button
        login.setOnClickListener {
            val credentials = validateLoginData()
            if (credentials != null)
                DetectConfigurationFragment.newInstance(credentials).show(requireFragmentManager(), null)
        }

        val forgotPassword = v.findViewById<View>(R.id.forgot_password) as TextView
        forgotPassword.setOnClickListener { WebViewActivity.openUrl(requireContext(), Constants.forgotPassword) }

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

    protected fun validateLoginData(): LoginCredentials? {
        var valid = true

        val userName = editUserName.text.toString()
        if (userName.isEmpty()) {
            editUserName.error = getString(R.string.login_email_address_error)
            valid = false
        } else {
            editUserName.error = null
        }

        val password = editUrlPassword.editText?.text.toString()
        if (password.isEmpty()) {
            editUrlPassword.error = getString(R.string.login_password_required)
            valid = false
        } else {
            editUrlPassword.error = null
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

        return if (valid) LoginCredentials(uri, userName, password) else null
    }

    companion object {
        fun newInstance(initialUsername: String?, initialPassword: String?): LoginCredentialsFragment {
            val ret = LoginCredentialsFragment()
            ret.initialUsername = initialUsername
            ret.initialPassword = initialPassword
            return ret
        }
    }
}
