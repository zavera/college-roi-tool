package com.example.collegeroitool.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class ResendEmailService {

    private static final Logger log = LoggerFactory.getLogger(ResendEmailService.class);
    private static final String RESEND_URL = "https://api.resend.com/emails";

    @Value("${resend.api.key:}")
    private String apiKey;

    @Value("${resend.from:Astra <onboarding@resend.dev>}")
    private String from;

    private final RestTemplate restTemplate = new RestTemplate();

    public void send(String to, String subject, String text) {
        new Thread(() -> sendSync(to, subject, text), "resend-email").start();
    }

    private void sendSync(String to, String subject, String text) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[resend] RESEND_API_KEY not set — skipping email to={}", to);
            return;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> body = Map.of(
                "from",    from,
                "to",      List.of(to),
                "subject", subject,
                "text",    text
            );

            ResponseEntity<Map> response = restTemplate.postForEntity(
                RESEND_URL, new HttpEntity<>(body, headers), Map.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("[resend] sent successfully to={}", to);
            } else {
                log.warn("[resend] unexpected status={} to={}", response.getStatusCode(), to);
            }
        } catch (Exception e) {
            log.error("[resend] failed to={} error={}", to, e.getMessage(), e);
        }
    }
}
