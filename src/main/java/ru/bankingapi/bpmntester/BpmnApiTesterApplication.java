package ru.bankingapi.bpmntester;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

import java.security.Security;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

@SpringBootApplication
@EnableAsync
public class BpmnApiTesterApplication {

    static {
        Security.addProvider(new BouncyCastleProvider());
        
        loadEnvFile();
    }

    public static void main(String[] args) {
        SpringApplication.run(BpmnApiTesterApplication.class, args);
    }
    
    private static void loadEnvFile() {
        try {
            java.nio.file.Path envFile = java.nio.file.Paths.get(".env");
            
            if (java.nio.file.Files.exists(envFile)) {
                java.nio.file.Files.lines(envFile).forEach(line -> {
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("#") && line.contains("=")) {
                        String[] parts = line.split("=", 2);
                        String key = parts[0].trim();
                        String value = parts.length > 1 ? parts[1].trim() : "";
                        
                        if (!value.isEmpty() && System.getenv(key) == null) {
                            System.setProperty(key, value);
                        }
                    }
                });
                System.out.println("✅ Loaded .env file");
            }
        } catch (Exception e) {
            System.out.println("⚠️  Could not load .env file: " + e.getMessage());
        }
    }
}