package com.davocado.server.global.exception;

/**
 * Application-level exception carrying an {@link ErrorCode}.
 *
 * <p>Throw this from services to signal a well-defined failure; {@link GlobalExceptionHandler}
 * translates it into the standard error response with the code's HTTP status.
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    /** Use when you need to override the default message (still keeps the code's status/code). */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
