package ru.bankingapi.bpmntester.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

@Service
@Slf4j
@RequiredArgsConstructor
public class OAuth2Service {

    @Qualifier("standardRestTemplate")
    private final RestTemplate standardRestTemplate;
    
    private final ObjectMapper objectMapper;

    @Value("${banking-api.auth-url}")
    private String authUrl;

    @Value("${banking-api.client-id}")
    private String clientId;

    @Value("${banking-api.client-secret}")
    private String clientSecret;

    private String cachedAccessToken;
    private Instant tokenExpiryTime;
    private final ReentrantLock tokenLock = new ReentrantLock();

    public String getAccessToken() {
        tokenLock.lock();
        try {
            if (isTokenValid()) {
                return cachedAccessToken;
            }
            return fetchNewAccessToken();
        } finally {
            tokenLock.unlock();
        }
    }

    public String refreshAccessToken() {
        tokenLock.lock();
        try {
            return fetchNewAccessToken();
        } finally {
            tokenLock.unlock();
        }
    }

    private boolean isTokenValid() {
        if (cachedAccessToken == null || tokenExpiryTime == null) {
            return false;
        }
        return Instant.now().isBefore(tokenExpiryTime.minusSeconds(60));
    }

    private String fetchNewAccessToken() {
        try {
            log.info("Fetching VBank client_token with client_id: {}", clientId);
            
            if (clientId == null || clientId.isEmpty()) {
                throw new RuntimeException("Client ID not configured");
            }
            
            if (clientSecret == null || clientSecret.isEmpty()) {
                throw new RuntimeException("Client Secret not configured");
            }

            // VBank использует JSON body для /auth/login
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> body = new HashMap<>();
            body.put("client_id", clientId);
            body.put("client_secret", clientSecret);

            HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

            log.debug("Sending auth request to VBank: {}", authUrl);
            
            ResponseEntity<String> response = standardRestTemplate.exchange(
                authUrl,
                HttpMethod.POST,
                request,
                String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode tokenResponse = objectMapper.readTree(response.getBody());
                
                if (!tokenResponse.has("access_token")) {
                    throw new RuntimeException("No access_token in response");
                }
                
                cachedAccessToken = tokenResponse.get("access_token").asText();
                
                // VBank токены живут 24 часа
                int expiresIn = tokenResponse.has("expires_in") 
                    ? tokenResponse.get("expires_in").asInt() 
                    : 86400;
                
                tokenExpiryTime = Instant.now().plusSeconds(expiresIn);

                log.info("VBank client_token obtained, valid for {} hours", expiresIn / 3600);
                return cachedAccessToken;
            } else {
                throw new RuntimeException("Failed to obtain token: HTTP " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Error obtaining VBank token: {}", e.getMessage(), e);
            throw new RuntimeException("Cannot obtain VBank token: " + e.getMessage(), e);
        }
    }

    public HttpHeaders createAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(getAccessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}