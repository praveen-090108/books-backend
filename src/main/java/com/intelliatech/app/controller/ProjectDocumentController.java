package com.intelliatech.app.controller;

import com.intelliatech.app.service.ProjectDocumentService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/projects/{projectType}/{projectId}/documents")
@RequiredArgsConstructor
public class ProjectDocumentController {
    private final ProjectDocumentService service;

    @GetMapping
    @PreAuthorize("@permissionGuard.can('projects',#projectType,'VIEW')")
    public List<ProjectDocumentService.Document> list(@PathVariable String projectType, @PathVariable Long projectId) {
        return service.list(projectType, projectId);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@permissionGuard.can('projects',#projectType,'EDIT')")
    public ProjectDocumentService.Document upload(@PathVariable String projectType, @PathVariable Long projectId,
                                                   @RequestPart("file") MultipartFile file) {
        return service.upload(projectType, projectId, file);
    }

    @GetMapping("/{documentId}/download")
    @PreAuthorize("@permissionGuard.can('projects',#projectType,'VIEW')")
    public ResponseEntity<byte[]> download(@PathVariable String projectType, @PathVariable Long projectId,
                                           @PathVariable Long documentId, @RequestParam(defaultValue = "false") boolean inline) {
        var file = service.download(projectType, projectId, documentId);
        var disposition = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(file.fileName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(file.contentType() == null ? "application/octet-stream" : file.contentType()))
                .body(file.content());
    }

    @DeleteMapping("/{documentId}")
    @PreAuthorize("@permissionGuard.can('projects',#projectType,'EDIT')")
    public ResponseEntity<Void> remove(@PathVariable String projectType, @PathVariable Long projectId,
                                       @PathVariable Long documentId) {
        service.remove(projectType, projectId, documentId);
        return ResponseEntity.noContent().build();
    }
}
