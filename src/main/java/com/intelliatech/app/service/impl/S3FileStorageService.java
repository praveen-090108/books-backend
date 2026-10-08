package com.intelliatech.app.service.impl;

import com.intelliatech.app.config.S3StorageProperties;
import com.intelliatech.app.config.LocalStorageProperties;
import com.intelliatech.app.dto.response.FileUploadResponse;
import com.intelliatech.app.service.FileStorageService;
import com.intelliatech.app.util.S3StorageUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class S3FileStorageService implements FileStorageService {

    private static final long MAX_LOGO_SIZE_BYTES = 2 * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/png",
            "image/jpeg",
            "image/svg+xml",
            "image/webp"
    );
    private static final long MAX_PAYMENT_ATTACHMENT_SIZE_BYTES = 10 * 1024 * 1024;
    private static final Set<String> ALLOWED_PAYMENT_ATTACHMENT_TYPES = Set.of(
            "application/pdf",
            "image/png",
            "image/jpeg"
    );
    private static final Set<String> ALLOWED_PURCHASE_ORDER_ATTACHMENT_TYPES = Set.of(
            "application/pdf",
            "image/png",
            "image/jpeg",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );
    private static final Set<String> ALLOWED_PROJECT_DOCUMENT_TYPES = Set.of(
            "application/pdf", "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "text/csv", "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/plain", "image/png", "image/jpeg", "image/webp",
            "application/zip", "application/x-zip-compressed"
    );
    private static final long MAX_ASSET_IMAGE_SIZE_BYTES = 10 * 1024 * 1024;
    private static final Set<String> ALLOWED_ASSET_IMAGE_TYPES = Set.of(
            "image/png", "image/jpeg", "image/webp"
    );

    private final S3StorageProperties properties;
    private final LocalStorageProperties localProperties;
    private final S3StorageUtil s3StorageUtil;

    @Override
    public FileUploadResponse uploadBrandingLogo(MultipartFile file) {
        validateFile(file, MAX_LOGO_SIZE_BYTES, ALLOWED_CONTENT_TYPES,
                "Logo file is required.", "Logo file size must be 2 MB or less.",
                "Only PNG, JPG, SVG, and WEBP logo files are supported.");
        if (!properties.complete()) {
            throw new IllegalStateException(
                    "Branding logo storage is not configured. Set AWS_S3_BUCKET and AWS_REGION; " +
                    "then grant the backend IAM role permission to upload to that bucket."
            );
        }
        return upload(file, "branding/logo/", "logo", "Unable to upload branding logo to S3");
    }

    @Override
    public FileUploadResponse uploadPaymentAttachment(MultipartFile file) {
        validateFile(file, MAX_PAYMENT_ATTACHMENT_SIZE_BYTES, ALLOWED_PAYMENT_ATTACHMENT_TYPES,
                "Payment attachment is required.", "Payment attachment size must be 10 MB or less.",
                "Only PDF, PNG, and JPG payment attachments are supported.");
        return upload(file, "payments/attachments/", "payment-attachment", "Unable to read payment attachment");
    }

    @Override
    public FileUploadResponse uploadPurchaseOrderAttachment(MultipartFile file) {
        validateFile(file, MAX_PAYMENT_ATTACHMENT_SIZE_BYTES, ALLOWED_PURCHASE_ORDER_ATTACHMENT_TYPES,
                "Purchase order attachment is required.", "Purchase order attachment size must be 10 MB or less.",
                "Only PDF, PNG, JPG, DOC, DOCX, XLS, and XLSX purchase order attachments are supported.");
        return upload(file, "purchase-orders/attachments/", "purchase-order-attachment",
                "Unable to read purchase order attachment");
    }

    @Override
    public FileUploadResponse uploadBillAttachment(MultipartFile file) {
        validateFile(file, MAX_PAYMENT_ATTACHMENT_SIZE_BYTES, ALLOWED_PURCHASE_ORDER_ATTACHMENT_TYPES,
                "Bill attachment is required.", "Bill attachment size must be 10 MB or less.",
                "Only PDF, PNG, JPG, DOC, DOCX, XLS, and XLSX Bill attachments are supported.");
        return upload(file, "bills/attachments/", "bill-attachment", "Unable to read Bill attachment");
    }

    @Override
    public FileUploadResponse uploadLeadProfile(MultipartFile file) {
        validateFile(file, MAX_PAYMENT_ATTACHMENT_SIZE_BYTES, ALLOWED_PURCHASE_ORDER_ATTACHMENT_TYPES,
                "Candidate profile is required.", "Candidate profile size must be 10 MB or less.",
                "Only PDF, PNG, JPG, DOC, DOCX, XLS, and XLSX candidate profiles are supported.");
        return upload(file, "leads/profiles/", "candidate-profile", "Unable to upload candidate profile");
    }

    @Override
    public FileUploadResponse uploadCustomerAttachment(MultipartFile file) {
        validateFile(file, MAX_PAYMENT_ATTACHMENT_SIZE_BYTES, ALLOWED_PURCHASE_ORDER_ATTACHMENT_TYPES,
                "Customer attachment is required.", "Customer attachment size must be 10 MB or less.",
                "Only PDF, PNG, JPG, DOC, DOCX, XLS, and XLSX customer attachments are supported.");
        return upload(file, "customers/attachments/", "customer-attachment", "Unable to upload customer attachment");
    }

    @Override
    public FileUploadResponse uploadProjectDocument(MultipartFile file, String projectType, Long projectId) {
        validateFile(file, MAX_PAYMENT_ATTACHMENT_SIZE_BYTES, ALLOWED_PROJECT_DOCUMENT_TYPES,
                "Project document is required.", "Project document size must be 10 MB or less.",
                "Unsupported project document type.");
        String safeType = "fixedCost".equals(projectType) ? "fixed-cost" : "staffing";
        return upload(file, "projects/" + safeType + "/" + projectId + "/documents/",
                "project-document", "Unable to upload project document");
    }

    @Override
    public FileUploadResponse uploadAssetImage(MultipartFile file, Long assetId) {
        validateFile(file, MAX_ASSET_IMAGE_SIZE_BYTES, ALLOWED_ASSET_IMAGE_TYPES,
                "Asset image is required.", "Asset image size must be 10 MB or less.",
                "Only PNG, JPG, JPEG, and WEBP asset images are supported.");
        if (!properties.complete()) {
            throw new IllegalStateException("Asset image storage is not configured. Set AWS_S3_BUCKET and AWS_REGION.");
        }
        return upload(file, "assets/" + assetId + "/images/", "asset-image",
                "Unable to upload asset image to S3");
    }

    @Override
    public StoredFile download(String key) {
        if (properties.active()) {
            var object = s3StorageUtil.download(key);
            return new StoredFile(object.content(), object.contentType());
        }
        if (!localProperties.enabled() || !StringUtils.hasText(localProperties.directory())) {
            throw new IllegalStateException("File storage is not configured.");
        }
        try {
            Path root = Path.of(localProperties.directory()).toAbsolutePath().normalize();
            Path source = root.resolve(key).normalize();
            if (!source.startsWith(root) || !Files.isRegularFile(source)) {
                throw new IllegalArgumentException("Project document does not exist.");
            }
            return new StoredFile(Files.readAllBytes(source), Files.probeContentType(source));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read project document.", exception);
        }
    }

    private FileUploadResponse upload(MultipartFile file, String folder, String fallbackName, String errorMessage) {
        String originalName = StringUtils.cleanPath(file.getOriginalFilename() == null ? fallbackName : file.getOriginalFilename());
        String extension = extension(originalName);
        String relativeKey = folder + UUID.randomUUID() + extension;
        String key = normalizedPrefix() + relativeKey;
        String contentType = file.getContentType();

        if (!properties.active()) {
            return uploadLocally(file, relativeKey, originalName, contentType, errorMessage);
        }
        return s3StorageUtil.upload(file, key, originalName, contentType, errorMessage);
    }

    private FileUploadResponse uploadLocally(
            MultipartFile file,
            String key,
            String originalName,
            String contentType,
            String errorMessage
    ) {
        if (!localProperties.enabled() || !StringUtils.hasText(localProperties.directory())) {
            throw new IllegalStateException(
                    "File storage is not configured. Enable either S3 storage or local storage."
            );
        }
        try {
            Path root = Path.of(localProperties.directory()).toAbsolutePath().normalize();
            Path destination = root.resolve(key).normalize();
            if (!destination.startsWith(root)) {
                throw new IllegalArgumentException("Invalid upload path.");
            }
            Files.createDirectories(destination.getParent());
            Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
            return new FileUploadResponse(key, localPublicUrl(key), originalName, contentType, file.getSize());
        } catch (IOException exception) {
            throw new IllegalStateException(errorMessage, exception);
        }
    }

    private String localPublicUrl(String key) {
        String baseUrl = StringUtils.hasText(localProperties.publicBaseUrl())
                ? localProperties.publicBaseUrl().trim()
                : "http://127.0.0.1:8080/uploads";
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl + "/" + key;
    }

    private void validateFile(
            MultipartFile file,
            long maxSize,
            Set<String> allowedTypes,
            String requiredMessage,
            String sizeMessage,
            String typeMessage
    ) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(requiredMessage);
        }
        if (file.getSize() > maxSize) {
            throw new IllegalArgumentException(sizeMessage);
        }
        if (!allowedTypes.contains(file.getContentType())) {
            throw new IllegalArgumentException(typeMessage);
        }
    }

    private String normalizedPrefix() {
        if (!StringUtils.hasText(properties.keyPrefix())) {
            return "";
        }
        String prefix = properties.keyPrefix().trim();
        return prefix.endsWith("/") ? prefix : prefix + "/";
    }

    private String extension(String fileName) {
        int index = fileName.lastIndexOf('.');
        if (index < 0) return "";
        return fileName.substring(index).toLowerCase(Locale.ROOT);
    }

}
