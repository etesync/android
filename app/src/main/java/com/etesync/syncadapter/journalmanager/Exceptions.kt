package com.etesync.syncadapter.journalmanager

import at.bitfire.cert4android.Constants
import okhttp3.Response
import okio.Buffer
import java.io.IOException
import java.io.Serializable
import java.security.GeneralSecurityException

class Exceptions {
    class AssociateNotAllowedException(response: Response, message: String?) : HttpException(response, message)

    class UnauthorizedException(response: Response, message: String?) : HttpException(response, message)

    class UserInactiveException(response: Response, message: String?) : HttpException(response, message)

    class ServiceUnavailableException : HttpException {
        var retryAfter: Long = 0

        constructor(message: String) : super(message) {
            this.retryAfter = 0
        }

        constructor(response: Response, message: String) : super(response, message) {
            this.retryAfter = java.lang.Long.valueOf(response.header("Retry-After", "0"))
        }
    }

    class IntegrityException(message: String) : GeneralSecurityException(message)


    open class GenericCryptoException(message: String) : Exception(message)

    class VersionTooNewException(message: String) : GenericCryptoException(message)

    open class HttpException : Exception, Serializable {
        internal val status: Int
        override val message: String

        val request: String?
        val response: String?

        constructor(message: String) : super(message) {
            this.message = message

            this.status = -1
            this.response = null
            this.request = this.response
        }

        constructor(status: Int, message: String) : super(status.toString() + " " + message) {
            this.status = status
            this.message = message

            response = null
            request = response
        }

        @JvmOverloads constructor(response: Response, custom_message: String? = null) : super(response.code().toString() + " " + response.message()) {

            status = response.code()
            message = custom_message ?: response.message()

            /* As we don't know the media type and character set of request and response body,
           only printable ASCII characters will be shown in clear text. Other octets will
           be shown as "[xx]" where xx is the hex value of the octet.
         */

            // format request
            val request = response.request()
            var formatted = StringBuilder()
            formatted.append(request.method()).append(" ").append(request.url().encodedPath()).append("\n")
            var headers = request.headers()
            for (name in headers.names()) {
                for (value in headers.values(name)) {
                    /* Redact authorization token. */
                    if (name == "Authorization") {
                        formatted.append(name).append(": ").append("XXXXXX").append("\n")
                    } else {
                        formatted.append(name).append(": ").append(value).append("\n")
                    }
                }
            }
            if (request.body() != null)
                try {
                    formatted.append("\n")
                    val buffer = Buffer()
                    request.body()!!.writeTo(buffer)
                    while (!buffer.exhausted())
                        appendByte(formatted, buffer.readByte())
                } catch (e: IOException) {
                    Constants.log.warning("Couldn't read request body")
                }

            this.request = formatted.toString()

            // format response
            formatted = StringBuilder()
            formatted.append(response.protocol()).append(" ").append(response.code()).append(" ").append(message).append("\n")
            headers = response.headers()
            for (name in headers.names())
                for (value in headers.values(name))
                    formatted.append(name).append(": ").append(value).append("\n")
            if (response.body() != null) {
                val body = response.body()
                try {
                    formatted.append("\n")
                    for (b in body!!.bytes())
                        appendByte(formatted, b)
                } catch (e: IOException) {
                    Constants.log.warning("Couldn't read response body")
                }

                body!!.close()
            }
            this.response = formatted.toString()
        }

        private fun appendByte(formatted: StringBuilder, b: Byte) {
            if (b == '\r'.toByte())
                formatted.append("[CR]")
            else if (b == '\n'.toByte())
                formatted.append("[LF]\n")
            else if (b >= 0x20 && b <= 0x7E)
            // printable ASCII
                formatted.append(b.toChar())
            else
                formatted.append("[" + Integer.toHexString(b.toInt() and 0xff) + "]")
        }
    }
}
