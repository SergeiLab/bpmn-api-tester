package ru.bankingapi.bpmntester.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class EndpointMappingService {

    private final Map<String, String> endpointMappings = new HashMap<>();
    private static final String DEMO_ACCOUNT_ID = UUID.randomUUID().toString();

    public EndpointMappingService() {
        initializeMappings();
    }

    private void initializeMappings() {
        // Map abstract BPMN endpoints to real Rewards Pay API
        
        // /accounts -> Get Balance
        endpointMappings.put("/accounts", 
            "/api/rb/rewardsPay/hackathon/v1/cards/accounts/external/" + DEMO_ACCOUNT_ID + "/rewards/balance");
        
        // /accounts/{account_id}/balances -> Get Balance
        endpointMappings.put("/accounts/{account_id}/balances", 
            "/api/rb/rewardsPay/hackathon/v1/cards/accounts/external/{account_id}/rewards/balance");
        
        // /payments -> Redemption
        endpointMappings.put("/payments", 
            "/api/rb/rewardsPay/hackathon/v1/cards/accounts/external/" + DEMO_ACCOUNT_ID + "/rewards/redemption");
        
        // /payments/{payment_id} -> Check last redemption (use same endpoint)
        endpointMappings.put("/payments/{payment_id}", 
            "/api/rb/rewardsPay/hackathon/v1/cards/accounts/external/" + DEMO_ACCOUNT_ID + "/rewards/balance");
        
        log.info("Initialized {} endpoint mappings for Rewards Pay API", endpointMappings.size());
        log.info("Using demo externalAccountID: {}", DEMO_ACCOUNT_ID);
    }

    public String mapEndpoint(String originalEndpoint) {
        if (originalEndpoint == null || originalEndpoint.isEmpty()) {
            return originalEndpoint;
        }

        // Direct match
        if (endpointMappings.containsKey(originalEndpoint)) {
            String mapped = endpointMappings.get(originalEndpoint);
            log.debug("Mapped endpoint: {} -> {}", originalEndpoint, mapped);
            return mapped;
        }

        // Pattern matching with path variables
        for (Map.Entry<String, String> entry : endpointMappings.entrySet()) {
            String pattern = entry.getKey();
            String replacement = entry.getValue();

            if (pattern.contains("{")) {
                String regexPattern = pattern.replaceAll("\\{[^}]+\\}", "([^/]+)");
                regexPattern = "^" + regexPattern + "$";

                Pattern p = Pattern.compile(regexPattern);
                Matcher m = p.matcher(originalEndpoint);

                if (m.matches()) {
                    String result = replacement;
                    for (int i = 1; i <= m.groupCount(); i++) {
                        // Replace path variable with captured value (or UUID if it looks like an ID)
                        String capturedValue = m.group(i);
                        // Use UUID format for account_id
                        if (capturedValue.matches("\\d+") || capturedValue.equals("account_id")) {
                            capturedValue = DEMO_ACCOUNT_ID;
                        }
                        result = result.replaceFirst("\\{[^}]+\\}", capturedValue);
                    }
                    log.debug("Mapped endpoint (pattern): {} -> {}", originalEndpoint, result);
                    return result;
                }
            }
        }

        log.debug("No mapping found for endpoint: {}", originalEndpoint);
        return originalEndpoint;
    }

    public void addMapping(String from, String to) {
        endpointMappings.put(from, to);
        log.info("Added endpoint mapping: {} -> {}", from, to);
    }
    
    public String getDemoAccountId() {
        return DEMO_ACCOUNT_ID;
    }
}