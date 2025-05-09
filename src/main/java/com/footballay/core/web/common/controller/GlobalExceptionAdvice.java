package com.footballay.core.web.common.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@Slf4j
@ControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE) // 가장 낮은 우선순위 설정
public class GlobalExceptionAdvice {

    @ExceptionHandler(Exception.class)
    public Object handleException(Exception e, HttpServletRequest request) {
        log.warn("Exception occurred: ", e);

        String uri = request.getRequestURI();
        if (uri.startsWith("/api")) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body("잘못된 요청입니다.");
        }

        return "redirect:/error";
    }
}