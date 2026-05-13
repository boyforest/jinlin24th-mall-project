package com.jinlin24th.jinlin.common.constant;

public final class HttpStatus {

    public static final int OK = 200;
    public static final int MULTIPLE_CHOICES = 300;

    public static final int BAD_REQUEST = 400;
    public static final int UNAUTHORIZED = 401;
    public static final int FORBIDDEN = 403;
    public static final int NOT_FOUND = 404;
    public static final int METHOD_NOT_ALLOWED = 405;
    public static final int CONFLICT = 409;
    public static final int UNSUPPORTED_MEDIA_TYPE = 415;

    public static final int TOO_MANY_REQUESTS = 429;

    public static final int INTERNAL_SERVER_ERROR = 500;

    private HttpStatus() {
    }
}
