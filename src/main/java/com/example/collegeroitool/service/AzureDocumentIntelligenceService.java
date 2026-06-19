package com.example.collegeroitool.service;

import io.callistotech.extraction.CallistoDocExtractor;
import io.callistotech.extraction.ExtractionException;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Thin Spring wrapper around {@link CallistoDocExtractor} from callisto-extraction-core.
 *
 * The heavy lifting — azure-ai-formrecognizer SDK client, getKeyValuePairs(), PDF preprocessing
 * (normalize/split/rotate/classify), SSN filtering — all lives in the shared library so every
 * Callisto Tech project gets the same pipeline without duplicating code.
 */
@Service
public class AzureDocumentIntelligenceService {

    @Value("${azure.docintel.endpoint:NOT_SET}")
    private String endpoint;

    @Value("${azure.docintel.key:NOT_SET}")
    private String apiKey;

    @Value("${premium.dev.bypass:false}")
    private boolean devBypass;

    private static final Logger log = Logger.getLogger(AzureDocumentIntelligenceService.class.getName());

    private CallistoDocExtractor extractor;

    @PostConstruct
    void init() {
        boolean unconfigured = endpoint == null || endpoint.isBlank()
            || endpoint.contains("NOT_SET") || apiKey == null || apiKey.isBlank()
            || apiKey.contains("NOT_SET");
        if (unconfigured) {
            log.warning("Azure Document Intelligence not configured — extraction will use dev stubs");
            return;
        }
        try {
            extractor = CallistoDocExtractor.builder()
                .endpoint(endpoint)
                .apiKey(apiKey)
                .pollTimeout(Duration.ofSeconds(90))
                .build();
        } catch (Exception e) {
            log.warning("Azure Document Intelligence init failed (" + e.getMessage() + ") — extraction disabled");
        }
    }

    /**
     * Extracts KV pairs from a file upload. Uses the full preprocessing pipeline:
     * normalise → analyse (prebuilt-document) → rotation retry if raster → split if oversized.
     */
    public Map<String, String> extractKeyValuePairs(MultipartFile file) throws Exception {
        if (extractor == null) {
            return devStubKeyValuePairs(file.getOriginalFilename());
        }
        try {
            return extractor.extractKeyValuePairs(file.getBytes());
        } catch (ExtractionException e) {
            throw new Exception(e.getMessage(), e.getCause());
        }
    }

    /**
     * Extracts KV pairs from a pre-signed SAS URL. Used for re-extraction of stored blobs.
     * Downloads bytes server-side and runs the same preprocessing pipeline.
     */
    public Map<String, String> extractKeyValuePairsFromUrl(String documentUrl) throws Exception {
        if (extractor == null) {
            return devStubKeyValuePairs(documentUrl);
        }
        // Download the blob bytes, then run the same pipeline as file upload
        try {
            java.net.URL url = new java.net.URI(documentUrl).toURL();
            byte[] bytes = url.openStream().readAllBytes();
            return extractor.extractKeyValuePairs(bytes);
        } catch (ExtractionException e) {
            throw new Exception(e.getMessage(), e.getCause());
        }
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
