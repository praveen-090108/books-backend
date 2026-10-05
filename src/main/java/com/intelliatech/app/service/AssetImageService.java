package com.intelliatech.app.service;

import com.intelliatech.app.dto.response.FileUploadResponse;
import com.intelliatech.app.entity.Asset;
import com.intelliatech.app.entity.AssetAttachment;
import com.intelliatech.app.repository.AssetAttachmentRepository;
import com.intelliatech.app.repository.AssetRepository;
import com.intelliatech.app.security.CurrentUserService;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class AssetImageService {
    private static final Long ORGANIZATION_ID = 1L;

    public record Image(Long id, Long assetId, String fileName, String fileUrl, String contentType,
                        Long fileSize, LocalDateTime uploadedAt) {}
    public record Download(String fileName, String contentType, byte[] content) {}

    private final AssetRepository assets;
    private final AssetAttachmentRepository attachments;
    private final FileStorageService storage;
    private final CurrentUserService users;

    @Transactional(readOnly = true)
    public List<Image> list(Long assetId) {
        asset(assetId);
        return attachments.findAllByAssetIdAndActiveTrueOrderByCreatedAtDescIdDesc(assetId)
                .stream().filter(item -> "IMAGE".equalsIgnoreCase(item.getAttachmentType()))
                .map(this::response).toList();
    }

    @Transactional
    public Image upload(Long assetId, MultipartFile file) {
        Asset asset = asset(assetId);
        FileUploadResponse uploaded = storage.uploadAssetImage(file, assetId);
        AssetAttachment attachment = new AssetAttachment();
        attachment.setAsset(asset);
        attachment.setFileName(uploaded.fileName());
        attachment.setFileUrl(uploaded.url());
        attachment.setStorageKey(uploaded.key());
        attachment.setContentType(uploaded.contentType());
        attachment.setFileSize(uploaded.size());
        attachment.setAttachmentType("IMAGE");
        attachment.setActive(true);
        attachment.setUploadedBy(users.getCurrentUserId());
        AssetAttachment saved = attachments.save(attachment);
        if (!StringUtils.hasText(asset.getImageUrl())) {
            asset.setImageUrl(uploaded.url());
            assets.save(asset);
        }
        return response(saved);
    }

    @Transactional(readOnly = true)
    public Download download(Long assetId, Long imageId) {
        asset(assetId);
        AssetAttachment image = image(assetId, imageId);
        if (!StringUtils.hasText(image.getStorageKey())) {
            throw new IllegalArgumentException("This legacy asset image is available only through its saved URL.");
        }
        var stored = storage.download(image.getStorageKey());
        String contentType = StringUtils.hasText(stored.contentType()) ? stored.contentType() : image.getContentType();
        return new Download(image.getFileName(), contentType, stored.content());
    }

    @Transactional
    public void remove(Long assetId, Long imageId) {
        asset(assetId);
        AssetAttachment image = image(assetId, imageId);
        image.setActive(false);
        image.setDeletedBy(users.getCurrentUserId());
        image.setDeletedAt(LocalDateTime.now());
        attachments.save(image);
        Asset asset = asset(assetId);
        if (image.getFileUrl().equals(asset.getImageUrl())) {
            String replacement = attachments.findAllByAssetIdAndActiveTrueOrderByCreatedAtDescIdDesc(assetId)
                    .stream().filter(item -> "IMAGE".equalsIgnoreCase(item.getAttachmentType()))
                    .map(AssetAttachment::getFileUrl).findFirst().orElse(null);
            asset.setImageUrl(replacement);
            assets.save(asset);
        }
    }

    private Asset asset(Long id) {
        return assets.findByIdAndOrganizationIdAndDeletedFalse(id, ORGANIZATION_ID)
                .orElseThrow(() -> new IllegalArgumentException("Asset does not exist."));
    }

    private AssetAttachment image(Long assetId, Long imageId) {
        return attachments.findByIdAndAssetIdAndActiveTrue(imageId, assetId)
                .filter(item -> "IMAGE".equalsIgnoreCase(item.getAttachmentType()))
                .orElseThrow(() -> new IllegalArgumentException("Asset image does not exist."));
    }

    private Image response(AssetAttachment image) {
        return new Image(image.getId(), image.getAsset().getId(), image.getFileName(), image.getFileUrl(),
                image.getContentType(), image.getFileSize(), image.getCreatedAt());
    }
}
