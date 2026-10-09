package com.intelliatech.app.util;

import com.intelliatech.app.config.S3StorageProperties;
import com.intelliatech.app.dto.response.FileUploadResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Shared low-level S3 upload utility. Business services remain responsible for
 * validating allowed file types and sizes before calling this class.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class S3StorageUtil {

    public record StoredObject(byte[] content, String contentType) {}

    private final S3Client s3Client;
    private final S3StorageProperties properties;

    public FileUploadResponse upload(
            MultipartFile file,
            String key,
            String originalName,
            String contentType
    ) {
        validateConfiguration();
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(key)
                    .contentType(contentType)
                    .cacheControl("public, max-age=31536000")
                    .build();
            s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            return new FileUploadResponse(key, publicUrl(key), originalName, contentType, file.getSize());
        } catch (IOException exception) {
            log.error("Unable to read upload stream for S3 bucket '{}' and key '{}'", properties.bucket(), key, exception);
            throw new IllegalStateException("We couldn't upload your document. Please try again.", exception);
        } catch (SdkException exception) {
            log.error("S3 upload failed for bucket '{}' and key '{}': {}",
                    properties.bucket(), key, exception.getMessage(), exception);
            throw new IllegalStateException("We couldn't upload your document. Please try again.", exception);
        }
    }

    public StoredObject download(String key) {
        validateConfiguration();
        try {
            ResponseBytes<GetObjectResponse> object = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(key)
                    .build());
            return new StoredObject(object.asByteArray(), object.response().contentType());
        } catch (SdkException exception) {
            log.error("S3 download failed for bucket '{}' and key '{}': {}",
                    properties.bucket(), key, exception.getMessage(), exception);
            throw new IllegalStateException("We couldn't download your document. Please try again.", exception);
        }
    }

    private void validateConfiguration() {
        if (!StringUtils.hasText(properties.bucket()) || !StringUtils.hasText(properties.region())) {
            throw new IllegalStateException("File storage is not configured. Set the S3 bucket and region.");
        }
    }

    private String publicUrl(String key) {
        if (StringUtils.hasText(properties.publicBaseUrl())) {
            String baseUrl = properties.publicBaseUrl().endsWith("/")
                    ? properties.publicBaseUrl().substring(0, properties.publicBaseUrl().length() - 1)
                    : properties.publicBaseUrl();
            return baseUrl + "/" + key;
        }
        return "https://" + properties.bucket() + ".s3." + properties.region() + ".amazonaws.com/" + key;
    }
}
