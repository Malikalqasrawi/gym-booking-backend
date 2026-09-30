package com.mycompany.gymbooking.exception;

import com.mycompany.gymbooking.dto.ErrorResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Catches exceptions thrown ANYWHERE in the controllers/services
 * and turns them into the same JSON error shape (ErrorResponse).
 *
 * Without this, the app would receive Spring's default error page, which is hard to read.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Our own errors: one method handles ALL subclasses thanks to polymorphism. */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException ex) {
        HttpStatus status = ex.getStatus();
        HttpHeaders headers = new HttpHeaders();
        ex.writeHeaders(headers);   // e.g. Retry-After on a 429 (empty for other errors)
        return ResponseEntity.status(status)
                .headers(headers)
                .body(ErrorResponse.of(status.value(), ex.getCode(), ex.getMessage()));
    }

    /** @Valid failed: collect every field's message, e.g. { "email": "Email is not valid" }. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        String firstMessage = fieldErrors.values().stream().findFirst().orElse("Invalid request");
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(400, "VALIDATION_FAILED", firstMessage, fieldErrors));
    }

    /** The body wasn't valid JSON at all. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleBadJson(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(400, "MALFORMED_JSON",
                        "Request body is not valid JSON, or a value has the wrong format (times must be HH:mm)"));
    }

    /** A required ?parameter is missing, e.g. /api/trainers/2/availability without ?date=... */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException ex) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(400, "MISSING_PARAMETER", "The '" + ex.getParameterName() + "' parameter is required"));
    }

    /** A value in the URL has the wrong type, e.g. /api/branches/abc (id must be a number). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(400, "INVALID_PARAMETER", "'" + ex.getValue() + "' is not a valid " + ex.getName()));
    }

    /** The URL doesn't match any endpoint. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoEndpoint(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(404, "NOT_FOUND", "No endpoint at this address"));
    }

    /** e.g. sending GET to an endpoint that only accepts POST. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleWrongMethod(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ErrorResponse.of(405, "METHOD_NOT_ALLOWED", ex.getMessage()));
    }

    /**
     * The body isn't JSON (e.g. XML or plain text). We only accept Content-Type: application/json.
     * Spring refuses these BEFORE reading the body, so an XML body is never parsed at all.
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleWrongContentType(HttpMediaTypeNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ErrorResponse.of(415, "UNSUPPORTED_MEDIA_TYPE",
                        "Send the request body as JSON (Content-Type: application/json)"));
    }

    /** The client asked for an answer in a format we don't produce (e.g. Accept: application/xml). */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<Void> handleNotAcceptable(HttpMediaTypeNotAcceptableException ex) {
        // No body: the client said it can't read JSON, and JSON is the only format we write
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build();
    }

    /**
     * Two people changed the same booking at the same moment (e.g. the trainer accepts while the member
     * cancels). The @Version check in Booking made the second save fail instead of overwriting the first.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleConcurrentChange(ObjectOptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(409, "BOOKING_CHANGED",
                        "This booking was changed at the same moment by someone else. Refresh and try again."));
    }

    /** Anything we didn't expect: log it for us, send a generic message to the app. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(500, "SERVER_ERROR", "Something went wrong on the server"));
    }
}
