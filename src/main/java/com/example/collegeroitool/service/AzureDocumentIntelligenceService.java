package com.example.collegeroitool.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class AzureDocumentIntelligenceService {

    @Value("${azure.docintel.endpoint}")
    private String endpoint;

    @Value("${azure.docintel.key}")
    private String apiKey;

    @Value("${premium.dev.bypass:false}")
    private boolean devBypass;

    private static final String DEV_STUB_KEY = "AZURE_DOCINTEL_KEY_NOT_SET";
    private static final String API_VERSION = "2024-11-30";

    private static final Pattern SSN_LABEL_PATTERN = Pattern.compile(
        "social\\s*security|\\bssn\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SSN_VALUE_PATTERN = Pattern.compile("\\d{3}-\\d{2}-\\d{4}");

    private final RestTemplate restTemplate;

    public AzureDocumentIntelligenceService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(30_000);
        restTemplate = new RestTemplate(factory);
    }

    /**
     * Extracts key-value pairs from a tax document via Azure Document Intelligence's
     * generic prebuilt-document model. Fields that look like SSNs (by label or value
     * pattern) are stripped before returning, as a baseline PII safeguard.
     */
    public Map<String, String> extractKeyValuePairs(MultipartFile file) throws Exception {
        if (devBypass && DEV_STUB_KEY.equals(apiKey)) {
            return devStubKeyValuePairs(file.getOriginalFilename());
        }

        String analyzeUrl = endpoint.replaceAll("/$", "")
            + "/documentintelligence/documentModels/prebuilt-document:analyze?api-version=" + API_VERSION;

        HttpHeaders postHeaders = new HttpHeaders();
        postHeaders.set("Ocp-Apim-Subscription-Key", apiKey);
        postHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);

        ResponseEntity<Void> postResponse = restTemplate.exchange(
            analyzeUrl, HttpMethod.POST, new HttpEntity<>(file.getBytes(), postHeaders), Void.class);

        String operationLocation = postResponse.getHeaders().getFirst("Operation-Location");
        if (operationLocation == null) {
            throw new IllegalStateException("Azure Document Intelligence did not return an Operation-Location header.");
        }

        HttpHeaders pollHeaders = new HttpHeaders();
        pollHeaders.set("Ocp-Apim-Subscription-Key", apiKey);
        HttpEntity<Void> pollEntity = new HttpEntity<>(pollHeaders);

        long deadline = System.currentTimeMillis() + 30_000;
        Map<String, Object> result = null;
        while (System.currentTimeMillis() < deadline) {
            @SuppressWarnings("unchecked")
            ResponseEntity<Map> pollResponse = restTemplate.exchange(
                operationLocation, HttpMethod.GET, pollEntity, Map.class);
            Map<String, Object> body = pollResponse.getBody();
            String status = body != null ? (String) body.get("status") : null;
            if ("succeeded".equals(status)) {
                result = body;
                break;
            }
            if ("failed".equals(status)) {
                throw new IllegalStateException("Azure Document Intelligence analysis failed for "
                    + file.getOriginalFilename());
            }
            Thread.sleep(1000);
        }
        if (result == null) {
            throw new IllegalStateException("Azure Document Intelligence analysis timed out for "
                + file.getOriginalFilename());
        }

        return parseKeyValuePairs(result);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> parseKeyValuePairs(Map<String, Object> result) {
        Map<String, String> parsed = new LinkedHashMap<>();
        Map<String, Object> analyzeResult = (Map<String, Object>) result.get("analyzeResult");
        if (analyzeResult == null) return parsed;

        List<Map<String, Object>> keyValuePairs = (List<Map<String, Object>>) analyzeResult.get("keyValuePairs");
        if (keyValuePairs == null) return parsed;

        for (Map<String, Object> kv : keyValuePairs) {
            Map<String, Object> keyObj = (Map<String, Object>) kv.get("key");
            Map<String, Object> valueObj = (Map<String, Object>) kv.get("value");
            String key = keyObj != null ? (String) keyObj.get("content") : null;
            String value = valueObj != null ? (String) valueObj.get("content") : null;
            if (key == null || value == null) continue;
            if (isSsnField(key, value)) continue;
            parsed.put(key.trim(), value.trim());
        }
        return parsed;
    }

    private boolean isSsnField(String key, String value) {
        return SSN_LABEL_PATTERN.matcher(key).find() || SSN_VALUE_PATTERN.matcher(value).find();
    }

    private Map<String, String> devStubKeyValuePairs(String filename) {
        Map<String, String> stub = new LinkedHashMap<>();
        stub.put("Employer name", "Sample Employer Inc.");
        stub.put("Wages, tips, other compensation", "$48,500.00");
        stub.put("Federal income tax withheld", "$4,200.00");
        stub.put("Filing status", "Single");
        stub.put("Adjusted gross income", "$48,500.00");
        return stub;
    }
}
