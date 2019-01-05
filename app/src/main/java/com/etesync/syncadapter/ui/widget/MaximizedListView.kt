/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.ListView

class MaximizedListView(context: Context, attrs: AttributeSet) : ListView(context, attrs) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        val widthMode = View.MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = View.MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = View.MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = View.MeasureSpec.getSize(heightMeasureSpec)

        var width = 0
        var height = 0

        if (widthMode == View.MeasureSpec.EXACTLY || widthMode == View.MeasureSpec.AT_MOST)
            width = widthSize

        if (heightMode == View.MeasureSpec.EXACTLY)
            height = heightSize
        else {
            val listAdapter = adapter
            if (listAdapter != null) {
                val widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
                for (i in 0 until listAdapter.count) {
                    val listItem = listAdapter.getView(i, null, this)
                    listItem.measure(widthSpec, View.MeasureSpec.UNSPECIFIED)
                    height += listItem.measuredHeight
                }
                height += dividerHeight * (listAdapter.count - 1)
            }
        }

        setMeasuredDimension(width, height)
    }

}
