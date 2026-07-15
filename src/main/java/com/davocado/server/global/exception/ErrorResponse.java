package com.davocado.server.global.exception;

/**
 * Uniform error body serialized as:
 * <pre>
 * {
 *   "error": {
 *     "code": "ERROR_CODE",
 *     "message": "사람이 읽을 설명"
 *   }
 * }
 * </pre>
 */
public record ErrorResponse(ErrorBody error) {

    public record ErrorBody(String code, String message) {}

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(new ErrorBody(code, message));
    }

    public static ErrorResponse of(ErrorCode errorCode) {
        return of(errorCode.getCode(), errorCode.getDefaultMessage());
    }

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return of(errorCode.getCode(), message);
    }
}
