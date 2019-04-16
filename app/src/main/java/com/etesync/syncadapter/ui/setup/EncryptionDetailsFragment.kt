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
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.etesync.syncadapter.Constants
import com.etesync.syncadapter.R
import com.google.android.material.textfield.TextInputLayout

class EncryptionDetailsFragment : Fragment() {
    internal lateinit var editPassword: TextInputLayout


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val v = inflater.inflate(R.layout.login_encryption_details, container, false)

        val btnBack = v.findViewById<View>(R.id.back) as Button
        btnBack.setOnClickListener { fragmentManager!!.popBackStack() }

        val config = arguments!!.getSerializable(KEY_CONFIG) as BaseConfigurationFinder.Configuration

        val accountName = v.findViewById<View>(R.id.account_name) as TextView
        accountName.text = getString(R.string.login_encryption_account_label) + " " + config.userName

        editPassword = v.findViewById<View>(R.id.encryption_password) as TextInputLayout

        val btnCreate = v.findViewById<View>(R.id.create_account) as Button
        btnCreate.setOnClickListener(View.OnClickListener {
            if (validateEncryptionData(config) == null) {
                return@OnClickListener
            }

            SetupEncryptionFragment.newInstance(config).show(fragmentManager!!, null)
        })

        val extra_details = v.findViewById<View>(R.id.encryption_extra_info) as TextView
        extra_details.text = getString(R.string.login_encryption_extra_info, Constants.faqUri.buildUpon().appendEncodedPath("#securing-etesync").build().toString())

        return v
    }

    private fun validateEncryptionData(config: BaseConfigurationFinder.Configuration): BaseConfigurationFinder.Configuration? {
        var valid = true
        val password = editPassword.editText?.text.toString()
        if (password.isEmpty()) {
            editPassword.error = getString(R.string.login_password_required)
            valid = false
        }

        config.rawPassword = password

        return if (valid) config else null
    }

    companion object {

        private val KEY_CONFIG = "config"


        fun newInstance(config: BaseConfigurationFinder.Configuration): EncryptionDetailsFragment {
            val frag = EncryptionDetailsFragment()
            val args = Bundle(1)
            args.putSerializable(KEY_CONFIG, config)
            frag.arguments = args
            return frag
        }
    }
}
