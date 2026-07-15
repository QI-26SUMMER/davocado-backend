package com.davocado.server.global.common;

/**
 * Generic success wrapper for controller responses.
 *
 * <p>Use {@code ApiResponse.success(data)} to return payloads in a consistent envelope.
 * Error responses are produced separately by the exception layer (see
 * {@code global.exception.ErrorResponse}).
 *
 * @param <T> payload type
 */
public record ApiResponse<T>(T data) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(data);
    }
}
