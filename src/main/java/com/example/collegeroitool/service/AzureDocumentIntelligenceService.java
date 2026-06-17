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
    // formrecognizer path (prebuilt-document lives here, not under documentintelligence/)
    private static final String API_VERSION = "2022-08-31";
    private static final String MODEL_ID = "prebuilt-document";
    private static final long POLL_TIMEOUT_MS = 90_000;
    private static final long POLL_INTERVAL_MS = 2_500;

    private static final Pattern SSN_LABEL_PATTERN = Pattern.compile(
        "social\\s*security|\\bssn\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SSN_VALUE_PATTERN = Pattern.compile("\\d{3}-\\d{2}-\\d{4}");

    private final RestTemplate restTemplate;

    public AzureDocumentIntelligenceService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(15_000);
        restTemplate = new RestTemplate(factory);
    }

    /**
     * Extracts KV pairs by sending raw bytes (used at upload time when bytes are already in memory).
     * SSN fields are stripped before returning.
     */
    public Map<String, String> extractKeyValuePairs(MultipartFile file) throws Exception {
        if (devBypass && DEV_STUB_KEY.equals(apiKey)) {
            return devStubKeyValuePairs(file.getOriginalFilename());
        }
        HttpHeaders headers = new HttpHeaders();
        headers.set("Ocp-Apim-Subscription-Key", apiKey);
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        String operationLocation = submitAnalyze(new HttpEntity<>(file.getBytes(), headers));
        return parseKeyValuePairs(pollUntilDone(operationLocation, file.getOriginalFilename()));
    }

    /**
     * Extracts KV pairs from a pre-signed SAS URL (used for re-extraction of stored documents).
     * SSN fields are stripped before returning.
     */
    public Map<String, String> extractKeyValuePairsFromUrl(String documentUrl) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Ocp-Apim-Subscription-Key", apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, String> body = Map.of("urlSource", documentUrl);
        String operationLocation = submitAnalyze(new HttpEntity<>(body, headers));
        return parseKeyValuePairs(pollUntilDone(operationLocation, documentUrl));
    }

    private String submitAnalyze(HttpEntity<?> request) {
        String analyzeUrl = endpoint.replaceAll("/$", "")
            + "/formrecognizer/documentModels/" + MODEL_ID + ":analyze?api-version=" + API_VERSION;
        ResponseEntity<Void> response = restTemplate.exchange(analyzeUrl, HttpMethod.POST, request, Void.class);
        String location = response.getHeaders().getFirst("Operation-Location");
        if (location == null) throw new IllegalStateException(
            "Azure Document Intelligence did not return an Operation-Location header.");
        return location;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> pollUntilDone(String operationLocation, String docId) throws Exception {
        HttpHeaders pollHeaders = new HttpHeaders();
        pollHeaders.set("Ocp-Apim-Subscription-Key", apiKey);
        HttpEntity<Void> pollEntity = new HttpEntity<>(pollHeaders);

        long deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(POLL_INTERVAL_MS);
            ResponseEntity<Map> pollResponse = restTemplate.exchange(
                operationLocation, HttpMethod.GET, pollEntity, Map.class);
            Map<String, Object> body = pollResponse.getBody();
            String status = body != null ? (String) body.get("status") : null;
            if ("succeeded".equals(status)) return body;
            if ("failed".equals(status)) throw new IllegalStateException(
                "Azure Document Intelligence analysis failed for " + docId);
        }
        throw new IllegalStateException("Azure Document Intelligence analysis timed out for " + docId);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> parseKeyValuePairs(Map<String, Object> result) {
        Map<String, String> parsed = new LinkedHashMap<>();
        Map<String, Object> analyzeResult = (Map<String, Object>) result.get("analyzeResult");
        if (analyzeResult == null) return parsed;

        // Primary path: prebuilt-document returns human-readable keyValuePairs
        List<Map<String, Object>> keyValuePairs = (List<Map<String, Object>>) analyzeResult.get("keyValuePairs");
        if (keyValuePairs != null) {
            for (Map<String, Object> kv : keyValuePairs) {
                Map<String, Object> keyObj = (Map<String, Object>) kv.get("key");
                Map<String, Object> valueObj = (Map<String, Object>) kv.get("value");
                String key = keyObj != null ? (String) keyObj.get("content") : null;
                String value = valueObj != null ? (String) valueObj.get("content") : null;
                if (key == null || value == null || value.isBlank()) continue;
                if (isCheckboxNoise(value)) continue;
                if (isSsnField(key, value)) continue;
                parsed.put(key.trim(), value.trim());
            }
        }

        // Fallback: structured fields from typed models (prebuilt-tax.us etc.)
        if (parsed.isEmpty()) {
            List<Map<String, Object>> documents = (List<Map<String, Object>>) analyzeResult.get("documents");
            if (documents != null) {
                for (Map<String, Object> doc : documents) {
                    String docType = (String) doc.get("docType");
                    if (docType != null) parsed.put("FormType", docType);
                    Map<String, Object> fields = (Map<String, Object>) doc.get("fields");
                    if (fields == null) continue;
                    for (Map.Entry<String, Object> entry : fields.entrySet()) {
                        String key = entry.getKey();
                        Map<String, Object> fieldObj = (Map<String, Object>) entry.getValue();
                        String value = extractFieldValue(fieldObj);
                        if (value == null || value.isBlank()) continue;
                        if (isCheckboxNoise(value)) continue;
                        if (isSsnField(key, value)) continue;
                        parsed.put(key.trim(), value.trim());
                    }
                }
            }
        }

        return parsed;
    }

    @SuppressWarnings("unchecked")
    private String extractFieldValue(Map<String, Object> fieldObj) {
        if (fieldObj == null) return null;
        // Try typed value fields first, then fall back to raw content
        for (String key : new String[]{"valueString", "valueNumber", "valueInteger", "content"}) {
            Object v = fieldObj.get(key);
            if (v != null) return v.toString();
        }
        return null;
    }

    private boolean isCheckboxNoise(String value) {
        String v = value.strip();
        return v.contains(":unselected:") || v.equals(":selected:") || v.isBlank();
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
