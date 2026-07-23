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

    BAD_REQUEST(HttpStatus.BAD_REQUEST, "Invalid request"),
    VALIDATION_FAILED(HttpStatus.UNPROCESSABLE_ENTITY, "Input validation failed"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentication required"),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Token has expired"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid email or password"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Access denied"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "Email is already in use"),
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "Image file is too large"),
    NO_AVOCADO_DETECTED(HttpStatus.UNPROCESSABLE_ENTITY, "No avocado detected in the photo"),
    INFERENCE_SERVICE_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "Inference service is unavailable"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An internal server error occurred");

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
