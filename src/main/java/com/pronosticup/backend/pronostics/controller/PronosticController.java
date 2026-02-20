package com.pronosticup.backend.pronostics.controller;

import com.pronosticup.backend.pronostics.controller.dto.request.SavePronosticRequest;
import com.pronosticup.backend.pronostics.controller.dto.response.SavePronosticResponse;
import com.pronosticup.backend.pronostics.service.PronosticService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pronostics")
@RequiredArgsConstructor
public class PronosticController {

    private final PronosticService pronosticService;

    @PostMapping
    public ResponseEntity<SavePronosticResponse> save(@RequestBody SavePronosticRequest req) {
        return ResponseEntity.ok(pronosticService.saveFirstTime(req));
    }
}
