/**
 * Centralized error handling: {@link com.davocado.server.global.exception.ErrorCode} catalog,
 * the {@link com.davocado.server.global.exception.BusinessException} thrown by services, and the
 * {@link com.davocado.server.global.exception.GlobalExceptionHandler} that renders every failure
 * as the standard {@code {"error":{"code","message"}}} body.
 */
package com.davocado.server.global.exception;
