package com.orders.web;

import java.time.OffsetDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.orders.dtos.DOrdersDependencyErrorBody;
import com.orders.exception.UpstreamDependencyException;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class OrdersExceptionHandler {

    @ExceptionHandler(UpstreamDependencyException.class)
    public ResponseEntity<DOrdersDependencyErrorBody> handleUpstream(
            UpstreamDependencyException ex,
            HttpServletRequest request) {
        int code = ex.getStatusCode().value();
        HttpStatus resolved = HttpStatus.resolve(code);
        String errorPhrase = resolved != null ? resolved.getReasonPhrase() : "Error";
        DOrdersDependencyErrorBody body = DOrdersDependencyErrorBody.builder()
                .timestamp(OffsetDateTime.now().toString())
                .status(code)
                .error(errorPhrase)
                .path(request.getRequestURI())
                .service(ex.getDependencyService())
                .message(ex.getReason())
                .build();
        return ResponseEntity.status(ex.getStatusCode()).body(body);
    }

}
