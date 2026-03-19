package com.pronosticup.backend.tournaments.integration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class FootballDataClient {

    private final RestTemplateBuilder restTemplateBuilder;

    @Value("${football-data.base-url}")
    private String baseUrl;

    @Value("${football-data.token}")
    private String authToken;

    @Value("${football-data.competition.world-cup}")
    private String worldCupCompetitionId;

    @Value("${football-data.competition.euro-cup}")
    private String euroCupCompetitionId;

    public Map<String, Object> getWorldCupStandings() {
        String url = baseUrl + "/v4/competitions/" + worldCupCompetitionId + "/standings";
        return getForMap(url, "MUNDIAL", "standings");
    }

    public Map<String, Object> getWorldCupMatches() {
        String url = baseUrl + "/v4/competitions/" + worldCupCompetitionId + "/matches";
        return getForMap(url, "MUNDIAL", "matches-knockouts");
    }

    public Map<String, Object> getEuroCupStandings() {
        String url = baseUrl + "/v4/competitions/" + euroCupCompetitionId + "/standings";
        return getForMap(url, "EUROCOPA", "standings");
    }

    public Map<String, Object> getEuroCupMatches() {
        String url = baseUrl + "/v4/competitions/" + euroCupCompetitionId + "/matches";
        return getForMap(url, "EUROCOPA", "matches-knockouts");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getForMap(String url, String tournament, String type) {
        log.info("Iniciando llamada a football-data.org torneo={} type={}", tournament, type);

        try {
            RestTemplate restTemplate = restTemplateBuilder
                    .setConnectTimeout(Duration.ofSeconds(10))
                    .setReadTimeout(Duration.ofSeconds(20))
                    .build();

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Auth-Token", authToken);
            headers.setAccept(MediaType.parseMediaTypes(MediaType.APPLICATION_JSON_VALUE));

            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    Map.class
            );

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new IllegalStateException("Respuesta inválida. status=" + response.getStatusCode());
            }

            log.info("OK respuesta recibida de football-data.org torneo={} type={}", tournament, type);
            return (Map<String, Object>) response.getBody();

        } catch (RestClientException ex) {
            log.error("KO llamada a football-data.org torneo={} type={} error={}", tournament, type, ex.getMessage(), ex);
            throw ex;
        }
    }
}