package ru.bankingapi.bpmntester.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
import org.apache.hc.client5.http.socket.PlainConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.config.Registry;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.cert.X509Certificate;

@Configuration
@Slf4j
public class GostHttpClientConfig {

    @Value("${banking-api.gost.enabled:false}")
    private boolean gostEnabled;

    @Value("${banking-api.gost.certificate-path:}")
    private String certificatePath;

    @Value("${banking-api.gost.certificate-password:}")
    private String certificatePassword;

    @Value("${banking-api.gost.trust-all-certs:false}")
    private boolean trustAllCerts;

    /**
     * Standard RestTemplate for non-GOST requests
     */
    @Bean(name = "standardRestTemplate")
    RestTemplate standardRestTemplate() {
        return new RestTemplate();
    }

    /**
     * GOST-enabled RestTemplate with BouncyCastle support
     */
    @Bean(name = "gostRestTemplate")
    RestTemplate gostRestTemplate() throws Exception {
        if (!gostEnabled) {
            log.warn("GOST is disabled, returning standard RestTemplate");
            return standardRestTemplate();
        }

        SSLContext sslContext = createGostSslContext();
        SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(
            sslContext,
            new String[]{"TLSv1.2", "TLSv1.3"},
            null,
            new org.apache.hc.client5.http.ssl.DefaultHostnameVerifier()
        );

        Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder
            .<ConnectionSocketFactory>create()
            .register("https", sslSocketFactory)
            .register("http", PlainConnectionSocketFactory.getSocketFactory())
            .build();

        PoolingHttpClientConnectionManager connectionManager = 
            new PoolingHttpClientConnectionManager(socketFactoryRegistry);
        connectionManager.setMaxTotal(100);
        connectionManager.setDefaultMaxPerRoute(20);

        CloseableHttpClient httpClient = HttpClients.custom()
            .setConnectionManager(connectionManager)
            .build();

        HttpComponentsClientHttpRequestFactory requestFactory = 
            new HttpComponentsClientHttpRequestFactory(httpClient);
        requestFactory.setConnectTimeout(30000);

        log.info("GOST-enabled RestTemplate configured successfully");
        return new RestTemplate(requestFactory);
    }

    /**
     * Create SSLContext with GOST cipher suites support
     */
    private SSLContext createGostSslContext() throws Exception {
        SSLContext sslContext;

        if (trustAllCerts) {
            log.warn("Trust all certificates is ENABLED - use only for development!");
            sslContext = SSLContext.getInstance("TLS");
            
            TrustManager[] trustAllManager = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return null; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
            };
            
            sslContext.init(null, trustAllManager, new java.security.SecureRandom());
        } else {
            // Load KeyStore with GOST certificates
            KeyStore keyStore = loadGostKeyStore();
            
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm()
            );
            kmf.init(keyStore, certificatePassword.toCharArray());

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm()
            );
            tmf.init(keyStore);

            sslContext = SSLContext.getInstance("TLS", new BouncyCastleProvider());
            sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        }

        return sslContext;
    }

    /**
     * Load GOST KeyStore from certificate file
     */
    private KeyStore loadGostKeyStore() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12", new BouncyCastleProvider());
        
        try (FileInputStream fis = new FileInputStream(certificatePath)) {
            keyStore.load(fis, certificatePassword.toCharArray());
            log.info("GOST certificate loaded from: {}", certificatePath);
        } catch (Exception e) {
            log.error("Failed to load GOST certificate from: {}", certificatePath, e);
            throw new RuntimeException("Cannot load GOST certificate", e);
        }

        return keyStore;
    }
}