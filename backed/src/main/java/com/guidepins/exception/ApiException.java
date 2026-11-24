package com.guidepins.exception;

import org.springframework.http.HttpStatus;

/** 业务层携带安全、可直接向调用者展示的接口错误。 */
public class ApiException extends RuntimeException {
    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
