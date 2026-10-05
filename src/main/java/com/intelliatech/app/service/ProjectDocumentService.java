package com.intelliatech.app.service;

import com.intelliatech.app.dto.response.FileUploadResponse;
import com.intelliatech.app.entity.BusinessRecord;
import com.intelliatech.app.repository.BusinessRecordRepository;
import com.intelliatech.app.security.CurrentUserService;
import com.intelliatech.app.security.DataScopeService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ProjectDocumentService {
    public record Document(Long id, Long projectId, String projectType, String originalFileName,
                           String fileExtension, String mimeType, Long fileSize, Long uploadedBy,
                           String uploadedByName, LocalDateTime uploadedAt, String documentType,
                           Integer sowVersion) {}
    public record Download(String fileName, String contentType, byte[] content) {}

    private final JdbcTemplate jdbc;
    private final BusinessRecordRepository records;
    private final FileStorageService storage;
    private final CurrentUserService users;
    private final DataScopeService scopes;

    public List<Document> list(String projectType, Long projectId) {
        project(projectType, projectId);
        return jdbc.query("""
                SELECT d.id,d.project_id,d.project_type,d.original_file_name,d.file_extension,d.mime_type,
                       d.file_size,d.uploaded_by,COALESCE(r.party_name,u.name,u.email,'User'),d.uploaded_at,
                       d.document_type,d.sow_version
                FROM project_documents d LEFT JOIN app_users u ON u.id=d.uploaded_by
                LEFT JOIN business_records r ON r.id=u.resource_id
                WHERE d.project_id=? AND d.project_type=? AND d.active=TRUE ORDER BY d.uploaded_at DESC,d.id DESC
                """, (rs, row) -> new Document(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4),
                rs.getString(5), rs.getString(6), rs.getLong(7), rs.getLong(8), rs.getString(9),
                rs.getTimestamp(10).toLocalDateTime(), rs.getString(11), (Integer) rs.getObject(12)), projectId, normalizedType(projectType));
    }

    @Transactional
    public Document upload(String projectType, Long projectId, MultipartFile file) {
        String type = normalizedType(projectType);
        project(type, projectId);
        FileUploadResponse uploaded = storage.uploadProjectDocument(file, type, projectId);
        String extension = extension(uploaded.fileName());
        jdbc.update("""
                INSERT INTO project_documents(project_id,project_type,original_file_name,storage_key,file_extension,
                mime_type,file_size,uploaded_by) VALUES(?,?,?,?,?,?,?,?)
                """, projectId, type, uploaded.fileName(), uploaded.key(), extension, uploaded.contentType(),
                uploaded.size(), users.getCurrentUserId());
        Long id = jdbc.queryForObject("SELECT MAX(id) FROM project_documents WHERE project_id=? AND project_type=?",
                Long.class, projectId, type);
        return list(type, projectId).stream().filter(item -> item.id().equals(id)).findFirst().orElseThrow();
    }

    public Download download(String projectType, Long projectId, Long documentId) {
        String type = normalizedType(projectType);
        project(type, projectId);
        var row = jdbc.query("SELECT original_file_name,storage_key,mime_type FROM project_documents WHERE id=? AND project_id=? AND project_type=? AND active=TRUE",
                rs -> rs.next() ? new String[]{rs.getString(1), rs.getString(2), rs.getString(3)} : null,
                documentId, projectId, type);
        if (row == null) throw new IllegalArgumentException("Project document does not exist.");
        var stored = storage.download(row[1]);
        return new Download(row[0], StringUtils.hasText(stored.contentType()) ? stored.contentType() : row[2], stored.content());
    }

    @Transactional
    public void remove(String projectType, Long projectId, Long documentId) {
        String type = normalizedType(projectType);
        project(type, projectId);
        Integer sowDocument = jdbc.queryForObject("SELECT COUNT(*) FROM staffing_project_sow WHERE document_id=?", Integer.class, documentId);
        if (sowDocument != null && sowDocument > 0) {
            throw new IllegalArgumentException("Signed SOW documents are retained in SOW History and cannot be removed.");
        }
        int changed = jdbc.update("UPDATE project_documents SET active=FALSE,deleted_by=?,deleted_at=CURRENT_TIMESTAMP WHERE id=? AND project_id=? AND project_type=? AND active=TRUE",
                users.getCurrentUserId(), documentId, projectId, type);
        if (changed == 0) throw new IllegalArgumentException("Project document does not exist.");
    }

    private BusinessRecord project(String projectType, Long projectId) {
        String type = normalizedType(projectType);
        BusinessRecord project = records.findByModuleAndTypeAndId("projects", type, projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project does not exist."));
        scopes.validateRecordAccess(project.getCreatedBy());
        return project;
    }

    private String normalizedType(String value) {
        if ("fixedCost".equals(value) || "staffing".equals(value)) return value;
        throw new IllegalArgumentException("Unsupported project type.");
    }

    private String extension(String fileName) {
        int index = fileName == null ? -1 : fileName.lastIndexOf('.');
        return index < 0 ? "" : fileName.substring(index + 1).toUpperCase(Locale.ROOT);
    }
}
