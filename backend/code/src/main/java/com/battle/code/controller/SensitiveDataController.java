package com.battle.code.controller;

import com.battle.code.data.SensitiveDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/match")
@RequiredArgsConstructor
public class SensitiveDataController {

    private final SensitiveDataService sensitiveDataService;

    @DeleteMapping("/{matchId}/sensitive-data")
    public ResponseEntity<PurgeResponse> purge(
            @PathVariable String matchId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long userId = Long.parseLong(userDetails.getUsername());
        SensitiveDataService.PurgeResult result =
                sensitiveDataService.purgeForUser(matchId, userId);
        return ResponseEntity.ok(new PurgeResponse(
                matchId,
                result.submittedCodes(),
                result.replays()
        ));
    }

    public record PurgeResponse(String matchId, int submittedCodes, int replays) {
    }
}
