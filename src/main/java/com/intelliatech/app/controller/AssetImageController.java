package com.intelliatech.app.controller;

import com.intelliatech.app.service.AssetImageService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/asset-management/assets/{assetId}/images")
@RequiredArgsConstructor
public class AssetImageController {
    private final AssetImageService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<AssetImageService.Image> list(@PathVariable Long assetId) {
        return service.list(assetId);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public AssetImageService.Image upload(@PathVariable Long assetId, @RequestPart("file") MultipartFile file) {
        return service.upload(assetId, file);
    }

    @GetMapping("/{imageId}/content")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> content(@PathVariable Long assetId, @PathVariable Long imageId) {
        var image = service.download(assetId, imageId);
        var disposition = ContentDisposition.inline().filename(image.fileName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(StringUtils.hasText(image.contentType()) ? image.contentType() : "application/octet-stream"))
                .body(image.content());
    }

    @DeleteMapping("/{imageId}")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public ResponseEntity<Void> remove(@PathVariable Long assetId, @PathVariable Long imageId) {
        service.remove(assetId, imageId);
        return ResponseEntity.noContent().build();
    }
}
