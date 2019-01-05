/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.etesync.syncadapter.log

import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.exception.ExceptionUtils
import org.apache.commons.lang3.time.DateFormatUtils

import java.util.logging.Formatter
import java.util.logging.LogRecord

class PlainTextFormatter private constructor(private val logcat: Boolean) : Formatter() {

    override fun format(r: LogRecord): String {
        val builder = StringBuilder()

        if (!logcat)
            builder.append(DateFormatUtils.format(r.millis, "yyyy-MM-dd HH:mm:ss"))
                    .append(" ").append(r.threadID).append(" ")

        if (r.sourceClassName.replaceFirst("\\$.*".toRegex(), "") != r.loggerName)
            builder.append("[").append(shortClassName(r.sourceClassName)).append("] ")

        builder.append(r.message)

        if (r.thrown != null)
            builder.append("\nEXCEPTION ")
                    .append(ExceptionUtils.getStackTrace(r.thrown))

        if (r.parameters != null) {
            var idx = 1
            for (param in r.parameters)
                builder.append("\n\tPARAMETER #").append(idx++).append(" = ").append(param)
        }

        if (!logcat)
            builder.append("\n")

        return builder.toString()
    }

    private fun shortClassName(className: String): String? {
        val s = StringUtils.replace(className, "com.etesync.syncadapter.", "")
        return StringUtils.replace(s, "at.bitfire.", "")
    }

    companion object {
        val LOGCAT = PlainTextFormatter(true)
        val DEFAULT = PlainTextFormatter(false)
    }

}
