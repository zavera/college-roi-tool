package com.example.collegeroitool.service;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.sas.BlobSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.time.OffsetDateTime;

@Service
public class AzureBlobStorageService {

    private static final Logger log = LoggerFactory.getLogger(AzureBlobStorageService.class);
    private static final String NOT_SET = "NOT_SET";

    @Value("${azure.blob.connection-string:" + NOT_SET + "}")
    private String connectionString;

    @Value("${azure.blob.container-name:student-documents}")
    private String containerName;

    private BlobContainerClient containerClient;
    private boolean enabled = false;

    @PostConstruct
    public void init() {
        if (NOT_SET.equals(connectionString)) {
            log.warn("Azure Blob Storage not configured — document uploads will be disabled");
            return;
        }
        try {
            BlobServiceClient serviceClient = new BlobServiceClientBuilder()
                .connectionString(connectionString)
                .buildClient();
            containerClient = serviceClient.getBlobContainerClient(containerName);
            if (!containerClient.exists()) {
                containerClient.create();
            }
            enabled = true;
            log.info("Azure Blob Storage ready — container: {}", containerName);
        } catch (Exception e) {
            log.error("Azure Blob Storage init failed — document uploads disabled: {}", e.getMessage());
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Uploads bytes to Azure Blob Storage and returns the permanent blob URL (no SAS).
     * blobName should be a path like "students/42/1718630400000_W2.pdf".
     */
    public String upload(String blobName, byte[] content, String contentType) {
        BlobClient client = containerClient.getBlobClient(blobName);
        client.upload(new ByteArrayInputStream(content), content.length, true);
        return client.getBlobUrl();
    }

    /**
     * Generates a short-lived SAS URL for the given blob (for re-extraction or download).
     */
    public String generateSasUrl(String blobName, java.time.Duration validity) {
        BlobClient client = containerClient.getBlobClient(blobName);
        BlobSasPermission permission = new BlobSasPermission().setReadPermission(true);
        BlobServiceSasSignatureValues values = new BlobServiceSasSignatureValues(
            OffsetDateTime.now().plus(validity), permission);
        String sasToken = client.generateSas(values);
        return client.getBlobUrl() + "?" + sasToken;
    }
}
