package ru.bankingapi.bpmntester.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
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
                log.debug("Using cached access token");
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
            log.info("Forcing access token refresh");
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
            log.info("Fetching OAuth2 token from Keycloak with client_id: {}", clientId);
            
            if (clientId == null || clientId.isEmpty()) {
                throw new RuntimeException("Client ID not configured. Set BANKING_API_CLIENT_ID environment variable.");
            }
            
            if (clientSecret == null || clientSecret.isEmpty()) {
                throw new RuntimeException("Client Secret not configured. Set BANKING_API_CLIENT_SECRET environment variable.");
            }

            // Keycloak requires application/x-www-form-urlencoded
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("grant_type", "client_credentials");
            body.add("client_id", clientId);
            body.add("client_secret", clientSecret);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

            log.debug("Sending OAuth2 request to Keycloak: {}", authUrl);
            
            ResponseEntity<String> response = standardRestTemplate.exchange(
                authUrl,
                HttpMethod.POST,
                request,
                String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode tokenResponse = objectMapper.readTree(response.getBody());
                
                if (!tokenResponse.has("access_token")) {
                    throw new RuntimeException("No access_token in response from Keycloak");
                }
                
                cachedAccessToken = tokenResponse.get("access_token").asText();
                
                // Keycloak tokens typically expire in 300-3600 seconds
                int expiresIn = tokenResponse.has("expires_in") 
                    ? tokenResponse.get("expires_in").asInt() 
                    : 3600;
                
                tokenExpiryTime = Instant.now().plusSeconds(expiresIn);

                log.info("OAuth2 token obtained successfully, expires in {} seconds", expiresIn);
                return cachedAccessToken;
            } else {
                throw new RuntimeException("Failed to obtain OAuth2 token: HTTP " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Error obtaining OAuth2 token: {}", e.getMessage(), e);
            throw new RuntimeException("Cannot obtain OAuth2 token: " + e.getMessage(), e);
        }
    }

    public HttpHeaders createAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(getAccessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}