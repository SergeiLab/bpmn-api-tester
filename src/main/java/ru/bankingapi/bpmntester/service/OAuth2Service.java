package ru.bankingapi.bpmntester.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

            log.info("Obtaining new access token from: {}", authUrl);
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
            log.info("Fetching token with client_id: {}", clientId);
            
            if (clientId == null || clientId.isEmpty() || clientId.equals("your_client_id")) {
                throw new RuntimeException("Client ID not configured. Set BANKING_API_CLIENT_ID environment variable.");
            }
            
            if (clientSecret == null || clientSecret.isEmpty() || clientSecret.equals("your_client_secret")) {
                throw new RuntimeException("Client Secret not configured. Set BANKING_API_CLIENT_SECRET environment variable.");
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("grant_type", "client_credentials");
            body.add("client_id", clientId);
            body.add("client_secret", clientSecret);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = standardRestTemplate.exchange(
                authUrl,
                HttpMethod.POST,
                request,
                String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode tokenResponse = objectMapper.readTree(response.getBody());
                
                cachedAccessToken = tokenResponse.get("access_token").asText();
                int expiresIn = tokenResponse.get("expires_in").asInt();
                tokenExpiryTime = Instant.now().plusSeconds(expiresIn);

                log.info("Access token obtained successfully, expires in {} seconds", expiresIn);
                return cachedAccessToken;
            } else {
                throw new RuntimeException("Failed to obtain access token: " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Error obtaining access token: {}", e.getMessage(), e);
            throw new RuntimeException("Cannot obtain access token: " + e.getMessage(), e);
        }
    }

    public HttpHeaders createAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(getAccessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}