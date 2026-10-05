package com.intelliatech.app.service;

import com.intelliatech.app.dto.response.FileUploadResponse;
import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {

    record StoredFile(byte[] content, String contentType) {}

    FileUploadResponse uploadBrandingLogo(MultipartFile file);

    FileUploadResponse uploadPaymentAttachment(MultipartFile file);

    FileUploadResponse uploadPurchaseOrderAttachment(MultipartFile file);

    FileUploadResponse uploadBillAttachment(MultipartFile file);

    FileUploadResponse uploadLeadProfile(MultipartFile file);

    FileUploadResponse uploadCustomerAttachment(MultipartFile file);

    FileUploadResponse uploadProjectDocument(MultipartFile file, String projectType, Long projectId);

    FileUploadResponse uploadAssetImage(MultipartFile file, Long assetId);

    StoredFile download(String key);
}
