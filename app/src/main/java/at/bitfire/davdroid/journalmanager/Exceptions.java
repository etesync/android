package at.bitfire.davdroid.journalmanager;

public class Exceptions {
    public static class UnauthorizedException extends HttpException {
        public UnauthorizedException(String message) {
            super(401, message);
        }
    }

    public static class ServiceUnavailableException extends HttpException {
        public ServiceUnavailableException(String message) {
            super(message);
        }
    }

    public static class HttpException extends Exception {
        public HttpException(String message) {
            super(message);
        }

        public HttpException(int status, String message) {
            super(status + " " + message);
        }
    }

    public static class IntegrityException extends Exception {
        public IntegrityException(String message) {
            super(message);
        }
    }
}
