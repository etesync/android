/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter.ui.widget

import android.content.Context
import android.text.Editable
import android.util.AttributeSet
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import com.etesync.syncadapter.R

class EditPassword : LinearLayout {

    internal var editPassword: EditText

    val text: Editable
        get() = editPassword.text

    init {
        View.inflate(context, R.layout.edit_password, this)

        editPassword = findViewById<View>(R.id.password) as EditText
    }

    constructor(context: Context) : super(context) {}

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        editPassword.setHint(attrs.getAttributeResourceValue(NS_ANDROID, "hint", 0))
        editPassword.setText(attrs.getAttributeValue(NS_ANDROID, "text"))

        val checkShowPassword = findViewById<View>(R.id.show_password) as CheckBox
        checkShowPassword.setOnCheckedChangeListener { buttonView, isChecked ->
            var inputType = editPassword.inputType and EditorInfo.TYPE_MASK_VARIATION.inv()
            inputType = inputType or if (isChecked) EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD else EditorInfo.TYPE_TEXT_VARIATION_PASSWORD
            editPassword.inputType = inputType
        }
    }

    fun setError(error: CharSequence) {
        editPassword.error = error
    }

    fun setText(text: CharSequence) {
        editPassword.setText(text)
    }

    companion object {
        private val NS_ANDROID = "http://schemas.android.com/apk/res/android"
    }

}
