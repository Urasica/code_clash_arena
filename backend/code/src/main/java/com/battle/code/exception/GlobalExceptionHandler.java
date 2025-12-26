package com.battle.code.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.PrintWriter;
import java.io.StringWriter;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleAllException(Exception e) {
        // 1. 서버 콘솔에 에러 출력 (테스트)
        e.printStackTrace();

        // 2. 브라우저로 에러 내용 전송 (디버깅용)
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));

        return ResponseEntity.status(500).body("🔥 SERVER ERROR LOG:\n" + sw.toString());
    }
}