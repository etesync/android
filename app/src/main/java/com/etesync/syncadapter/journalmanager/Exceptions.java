package com.etesync.syncadapter.journalmanager;

import java.io.IOException;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.security.GeneralSecurityException;

import at.bitfire.cert4android.Constants;
import okhttp3.Headers;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;

public class Exceptions {
    public static class UnauthorizedException extends HttpException {
        public UnauthorizedException(Response response, String message) {
            super(response, message);
        }
    }

    public static class UserInactiveException extends HttpException {
        public UserInactiveException(Response response, String message) {
            super(response, message);
        }
    }

    public static class ServiceUnavailableException extends HttpException {
        public long retryAfter;

        public ServiceUnavailableException(String message) {
            super(message);
            this.retryAfter = 0;
        }

        public ServiceUnavailableException(Response response, String message) {
            super(response, message);
            this.retryAfter = Long.valueOf(response.header("Retry-After", "0"));
        }
    }

    public static class IntegrityException extends GeneralSecurityException {
        public IntegrityException(String message) {
            super(message);
        }
    }


    public static class GenericCryptoException extends Exception {
        public GenericCryptoException(String message) {
            super(message);
        }
    }

    public static class VersionTooNewException extends GenericCryptoException {
        public VersionTooNewException(String message) {
            super(message);
        }
    }

    public static class HttpException extends Exception implements Serializable {
        final int status;
        final String message;

        public final String request, response;

        public HttpException(String message) {
            super(message);
            this.message = message;

            this.status = -1;
            this.request = this.response = null;
        }

        public HttpException(int status, String message) {
            super(status + " " + message);
            this.status = status;
            this.message = message;

            request = response = null;
        }

        public HttpException(Response response) {
            this(response, null);
        }

        public HttpException(Response response, String custom_message) {
            super(response.code() + " " + response.message());

            status = response.code();
            message = (custom_message != null) ? custom_message : response.message();

        /* As we don't know the media type and character set of request and response body,
           only printable ASCII characters will be shown in clear text. Other octets will
           be shown as "[xx]" where xx is the hex value of the octet.
         */

            // format request
            Request request = response.request();
            StringBuilder formatted = new StringBuilder();
            formatted.append(request.method()).append(" ").append(request.url().encodedPath()).append("\n");
            Headers headers = request.headers();
            for (String name : headers.names()) {
                for (String value : headers.values(name)) {
                    /* Redact authorization token. */
                    if (name.equals("Authorization")) {
                        formatted.append(name).append(": ").append("XXXXXX").append("\n");
                    } else {
                        formatted.append(name).append(": ").append(value).append("\n");
                    }
                }
            }
            if (request.body() != null)
                try {
                    formatted.append("\n");
                    Buffer buffer = new Buffer();
                    request.body().writeTo(buffer);
                    while (!buffer.exhausted())
                        appendByte(formatted, buffer.readByte());
                } catch (IOException e) {
                    Constants.log.warning("Couldn't read request body");
                }
            this.request = formatted.toString();

            // format response
            formatted = new StringBuilder();
            formatted.append(response.protocol()).append(" ").append(response.code()).append(" ").append(message).append("\n");
            headers = response.headers();
            for (String name : headers.names())
                for (String value : headers.values(name))
                    formatted.append(name).append(": ").append(value).append("\n");
            if (response.body() != null) {
                ResponseBody body = response.body();
                try {
                    formatted.append("\n");
                    for (byte b : body.bytes())
                        appendByte(formatted, b);
                } catch (IOException e) {
                    Constants.log.warning("Couldn't read response body");
                }
                body.close();
            }
            this.response = formatted.toString();
        }

        private static void appendByte(StringBuilder formatted, byte b) {
            if (b == '\r')
                formatted.append("[CR]");
            else if (b == '\n')
                formatted.append("[LF]\n");
            else if (b >= 0x20 && b <= 0x7E)     // printable ASCII
                formatted.append((char) b);
            else
                formatted.append("[" + Integer.toHexString((int) b & 0xff) + "]");
        }
    }
}
