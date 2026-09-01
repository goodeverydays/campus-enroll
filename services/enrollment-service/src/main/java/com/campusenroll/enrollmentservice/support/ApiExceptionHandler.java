package com.campusenroll.enrollmentservice.support;

import com.campusenroll.enrollmentservice.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(EnrollmentBusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(
            EnrollmentBusinessException exception,
            HttpServletRequest request) {
        HttpStatus status = exception.code() == 40400 ? HttpStatus.NOT_FOUND : HttpStatus.CONFLICT;
        return ResponseEntity.status(status)
                .body(ApiResponse.error(exception.code(), exception.getMessage(), RequestIds.from(request)));
    }

    @ExceptionHandler(EnrollmentDependencyException.class)
    public ResponseEntity<ApiResponse<Void>> handleDependency(
            EnrollmentDependencyException exception,
            HttpServletRequest request) {
        LOGGER.warn("Enrollment dependency failure", exception);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(50300, "Enrollment dependency is unavailable", RequestIds.from(request)));
    }

    @ExceptionHandler({
            ConstraintViolationException.class,
            HandlerMethodValidationException.class,
            MethodArgumentTypeMismatchException.class,
            MissingRequestHeaderException.class,
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(
            Exception exception,
            HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(40000, "Invalid request parameters", RequestIds.from(request)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(
            Exception exception,
            HttpServletRequest request) {
        LOGGER.error("Unhandled request failure", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(50000, "Internal server error", RequestIds.from(request)));
    }
}
