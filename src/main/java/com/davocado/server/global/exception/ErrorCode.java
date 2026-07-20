package com.davocado.server.global.exception;

import org.springframework.http.HttpStatus;

/**
 * Canonical set of business error codes.
 *
 * <p>Each value bundles a stable machine-readable code (the enum name), the HTTP status to
 * return, and a default human-readable message. The {@code code} field in the error JSON is the
 * enum name, so keep these names stable — clients may switch on them.
 */
public enum ErrorCode {

    BAD_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다"),
    VALIDATION_FAILED(HttpStatus.UNPROCESSABLE_ENTITY, "입력값 검증에 실패했습니다"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다"),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "토큰이 만료되었습니다"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "리소스를 찾을 수 없습니다"),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다"),
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "이미지 용량이 너무 큽니다"),
    NO_AVOCADO_DETECTED(HttpStatus.UNPROCESSABLE_ENTITY, "사진에서 아보카도를 찾지 못했습니다"),
    INFERENCE_SERVICE_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "예측 서비스를 사용할 수 없습니다"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getStatus() {
        return status;
    }

    /** Machine-readable code exposed to clients (equal to the enum name). */
    public String getCode() {
        return name();
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
