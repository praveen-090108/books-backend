package com.intelliatech.app.controller;

import com.intelliatech.app.config.S3StorageProperties;
import com.intelliatech.app.entity.BusinessRecord;
import com.intelliatech.app.repository.BusinessRecordRepository;
import com.intelliatech.app.util.S3StorageUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

@RestController
@RequiredArgsConstructor
public class BrandingAssetController {

    private static final Pattern SAFE_FILE_NAME = Pattern.compile("[A-Za-z0-9._-]+");

    private final S3StorageUtil s3StorageUtil;
    private final S3StorageProperties properties;
    private final BusinessRecordRepository businessRecordRepository;
    private final ObjectMapper objectMapper;

    @GetMapping("/branding-assets/current")
    public ResponseEntity<byte[]> getCurrentBrandingLogo() {
        BusinessRecord branding = businessRecordRepository
                .findFirstByModuleAndTypeOrderByRecordDateDesc("settings", "branding")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String logoUrl = readLogoUrl(branding.getNotes());
        String fileName = logoFileName(logoUrl);
        if (!StringUtils.hasText(fileName)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return getBrandingLogo(fileName);
    }

    @GetMapping("/branding-assets/{fileName}")
    public ResponseEntity<byte[]> getBrandingLogo(@PathVariable String fileName) {
        if (!properties.enabled() || !SAFE_FILE_NAME.matcher(fileName).matches()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        String key = normalizedPrefix() + "branding/logo/" + fileName;
        try {
            S3StorageUtil.StoredObject object = s3StorageUtil.download(key);
            MediaType contentType = StringUtils.hasText(object.contentType())
                    ? MediaType.parseMediaType(object.contentType())
                    : MediaType.APPLICATION_OCTET_STREAM;
            return ResponseEntity.ok()
                    .header("X-Content-Type-Options", "nosniff")
                    .cacheControl(CacheControl.noCache())
                    .contentType(contentType)
                    .body(object.content());
        } catch (NoSuchKeyException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        } catch (S3Exception exception) {
            if (exception.statusCode() == HttpStatus.NOT_FOUND.value()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            }
            throw exception;
        }
    }

    private String normalizedPrefix() {
        if (!StringUtils.hasText(properties.keyPrefix())) {
            return "";
        }
        String prefix = properties.keyPrefix().trim();
        return prefix.endsWith("/") ? prefix : prefix + "/";
    }

    private String readLogoUrl(String notes) {
        if (!StringUtils.hasText(notes)) {
            return "";
        }
        try {
            JsonNode root = objectMapper.readTree(notes);
            return root.path("logoUrl").asText("");
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    private String logoFileName(String logoUrl) {
        if (!StringUtils.hasText(logoUrl)) {
            return "";
        }
        for (String marker : new String[]{"/branding/logo/", "/branding-assets/"}) {
            int markerIndex = logoUrl.indexOf(marker);
            if (markerIndex >= 0) {
                String value = logoUrl.substring(markerIndex + marker.length()).split("[?#]", 2)[0];
                return SAFE_FILE_NAME.matcher(value).matches() ? value : "";
            }
        }
        return "";
    }
}
