package com.battle.code.controller;

import com.battle.code.dto.MatchRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import com.battle.code.service.GameService;

import java.io.IOException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/test")
@CrossOrigin(origins = "*")
public class Testcontroller {
    private final GameService gameservice;

    @PostMapping("/match")
    public String test(@RequestBody MatchRequest request) {
        try {
            // DTO에서 코드를 꺼내서 전달
            return gameservice.runMatch(request.getUserCode());
        } catch (IOException | InterruptedException e) {
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }
}