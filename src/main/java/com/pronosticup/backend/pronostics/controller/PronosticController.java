package com.pronosticup.backend.pronostics.controller;

import com.pronosticup.backend.pronostics.controller.dto.request.SavePronosticRequest;
import com.pronosticup.backend.pronostics.controller.dto.request.UpdatePronosticRequest;
import com.pronosticup.backend.pronostics.controller.dto.response.PronosticClasificacionResponse;
import com.pronosticup.backend.pronostics.controller.dto.response.PronosticDetailResponse;
import com.pronosticup.backend.pronostics.controller.dto.response.SavePronosticResponse;
import com.pronosticup.backend.pronostics.controller.dto.response.UpdatePronosticResponse;
import com.pronosticup.backend.pronostics.service.PronosticService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pronostics")
@RequiredArgsConstructor
public class PronosticController {

    private final PronosticService pronosticService;

    @PostMapping
    public ResponseEntity<SavePronosticResponse> save(@RequestBody SavePronosticRequest req) {
        return ResponseEntity.ok(pronosticService.saveFirstTime(req));
    }

    @PutMapping("/{leagueId}/{pronosticId}/confirm")
    public void confirm(
            @PathVariable String leagueId,
            @PathVariable String pronosticId,
            @RequestParam Long ownerUserId
    ) {
        pronosticService.confirmPronostic(leagueId, pronosticId, ownerUserId);
    }

    @DeleteMapping("/{leagueId}/{pronosticId}")
    public void reject(
            @PathVariable String leagueId,
            @PathVariable String pronosticId,
            @RequestParam Long ownerUserId
    ) {
        pronosticService.rejectPronostic(leagueId, pronosticId, ownerUserId);
    }

    @GetMapping("/{pronosticId}")
    public ResponseEntity<PronosticDetailResponse> getPronosticDetail(@PathVariable String pronosticId) {
        return ResponseEntity.ok(pronosticService.getPronosticDetail(pronosticId));
    }

    @PutMapping("/{pronosticId}")
    public ResponseEntity<UpdatePronosticResponse> updatePronostic(
            @PathVariable String pronosticId,
            @RequestBody UpdatePronosticRequest request
    ) {
        return ResponseEntity.ok(pronosticService.updatePronostic(pronosticId, request));
    }

    @GetMapping("/classification/{leagueId}")
    public List<PronosticClasificacionResponse> getLeagueClassification(@PathVariable String leagueId) {
        return pronosticService.getLeagueClassification(leagueId);
    }
}
