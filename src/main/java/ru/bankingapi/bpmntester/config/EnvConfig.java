package ru.bankingapi.bpmntester.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class EnvConfig implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        ConfigurableEnvironment environment = applicationContext.getEnvironment();
        
        Path envFile = Paths.get(".env");
        
        if (Files.exists(envFile)) {
            try {
                Map<String, Object> envProps = new HashMap<>();
                
                Files.lines(envFile).forEach(line -> {
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("#") && line.contains("=")) {
                        String[] parts = line.split("=", 2);
                        String key = parts[0].trim();
                        String value = parts.length > 1 ? parts[1].trim() : "";
                        
                        if (!value.isEmpty()) {
                            envProps.put(key, value);
                            System.setProperty(key, value);
                        }
                    }
                });
                
                environment.getPropertySources().addFirst(new MapPropertySource("envFile", envProps));
                log.info("Loaded {} properties from .env file", envProps.size());
                
            } catch (IOException e) {
                log.warn("Failed to load .env file: {}", e.getMessage());
            }
        } else {
            log.info("No .env file found, using environment variables");
        }
    }
}