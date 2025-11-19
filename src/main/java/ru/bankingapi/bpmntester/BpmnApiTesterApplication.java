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
    }

    public static void main(String[] args) {
        SpringApplication.run(BpmnApiTesterApplication.class, args);
    }
}