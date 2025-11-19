package ru.bankingapi.bpmntester.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class EndpointMappingService {

    private final Map<String, String> endpointMappings = new HashMap<>();

    public EndpointMappingService() {
        initializeMappings();
    }

    private void initializeMappings() {
        // Auth - VBank endpoints
        // Маппим абстрактный вызов получения токена на реальный эндпоинт для client_token
        endpointMappings.put("/auth/bank-token", "/auth/login"); 
        
        // Accounts
        endpointMappings.put("/accounts", "/accounts");
        endpointMappings.put("/accounts/{account_id}/balances", "/accounts/{account_id}/balances");
        
        // Payments
        endpointMappings.put("/payments", "/payments");
        endpointMappings.put("/payments/{payment_id}", "/payments/{payment_id}");
        
        log.info("Initialized {} VBank endpoint mappings", endpointMappings.size());
    }

    public String mapEndpoint(String originalEndpoint) {
        if (originalEndpoint == null || originalEndpoint.isEmpty()) {
            return originalEndpoint;
        }

        // Прямое совпадение
        if (endpointMappings.containsKey(originalEndpoint)) {
            String mapped = endpointMappings.get(originalEndpoint);
            log.debug("Mapped endpoint: {} -> {}", originalEndpoint, mapped);
            return mapped;
        }

        // Совпадение по шаблонам (для path variables)
        for (Map.Entry<String, String> entry : endpointMappings.entrySet()) {
            String pattern = entry.getKey();
            String replacement = entry.getValue();

            if (pattern.contains("{")) {
                // Превращаем шаблон вида /accounts/{id}/balances в regex
                String regexPattern = pattern.replaceAll("\\{[^}]+\\}", "([^/]+)");
                regexPattern = "^" + regexPattern + "$";

                Pattern p = Pattern.compile(regexPattern);
                Matcher m = p.matcher(originalEndpoint);

                if (m.matches()) {
                    String result = replacement;
                    // Подставляем захваченные группы обратно в шаблон
                    for (int i = 1; i <= m.groupCount(); i++) {
                        result = result.replaceFirst("\\{[^}]+\\}", m.group(i));
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
}