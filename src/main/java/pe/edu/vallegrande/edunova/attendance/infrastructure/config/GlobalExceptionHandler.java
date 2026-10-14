package pe.edu.vallegrande.edunova.attendance.infrastructure.config;

import pe.edu.vallegrande.edunova.attendance.domain.exception.BusinessException;
import pe.edu.vallegrande.edunova.attendance.domain.exception.ConflictException;
import pe.edu.vallegrande.edunova.attendance.domain.exception.ForbiddenException;
import pe.edu.vallegrande.edunova.attendance.domain.exception.NotFoundException;
import pe.edu.vallegrande.edunova.attendance.domain.exception.ServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Mono<Map<String, Object>> handleNotFoundException(NotFoundException ex) {
        return errorResponse("NOT_FOUND", ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(ConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Mono<Map<String, Object>> handleConflictException(ConflictException ex) {
        return errorResponse("ATTENDANCE_ALREADY_EXISTS", ex.getMessage(), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(ForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Mono<Map<String, Object>> handleForbiddenException(ForbiddenException ex) {
        return errorResponse("FORBIDDEN", ex.getMessage(), HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(WebExchangeBindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Mono<Map<String, Object>> handleValidationException(WebExchangeBindException ex) {
        List<Map<String, String>> details = ex.getFieldErrors().stream()
            .map(error -> {
                Map<String, String> detail = new LinkedHashMap<>();
                detail.put("field", error.getField());
                detail.put("message", error.getDefaultMessage());
                return detail;
            })
            .toList();
        String message = details.isEmpty() ? "Solicitud inválida" : details.get(0).get("message");
        return errorResponse("VALIDATION_ERROR", message, HttpStatus.BAD_REQUEST, details);
    }

    @ExceptionHandler({BusinessException.class, IllegalArgumentException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Mono<Map<String, Object>> handleBadRequestException(Exception ex) {
        return errorResponse("BUSINESS_RULE_VIOLATION", resolveMessage(ex), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Mono<Map<String, Object>> handleServiceUnavailableException(ServiceUnavailableException ex) {
        log.warn("Dependency unavailable [{}]: {}", ex.getCode(), ex.getMessage());
        return errorResponse(ex.getCode(), ex.getMessage(), HttpStatus.SERVICE_UNAVAILABLE);
    }
    
    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Mono<Map<String, Object>> handleRuntimeException(RuntimeException ex) {
        log.error("Unhandled runtime error", ex);
        return errorResponse("INTERNAL_ERROR", "Ocurrió un error inesperado. Intenta nuevamente.", HttpStatus.INTERNAL_SERVER_ERROR);
    }
    
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Mono<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("Unhandled error", ex);
        return errorResponse("INTERNAL_ERROR", "Ocurrió un error inesperado. Intenta nuevamente.", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private Mono<Map<String, Object>> errorResponse(String code, String message, HttpStatus status) {
        return errorResponse(code, message, status, List.of());
    }

    private Mono<Map<String, Object>> errorResponse(String code, String message, HttpStatus status,
                                                    List<Map<String, String>> details) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("code", code);
        response.put("message", message == null || message.isBlank() ? "Solicitud inválida" : message);
        response.put("error", message);
        response.put("status", status.value());
        response.put("details", details);
        response.put("timestamp", Instant.now().toString());
        String traceId = MDC.get("traceId");
        if (traceId != null && !traceId.isBlank()) {
            response.put("traceId", traceId);
        }
        return Mono.just(response);
    }

    private String resolveMessage(Exception ex) {
        if (ex instanceof WebExchangeBindException bindException) {
            return bindException.getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Solicitud inválida");
        }
        return ex.getMessage();
    }
}
