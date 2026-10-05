package com.intelliatech.app.service;

import com.intelliatech.app.dto.response.FileUploadResponse;
import com.intelliatech.app.entity.BusinessRecord;
import com.intelliatech.app.repository.BusinessRecordRepository;
import com.intelliatech.app.security.CurrentUserService;
import com.intelliatech.app.security.DataScopeService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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
public class StaffingProjectSowService {
    public record Sow(Long id, Long projectId, int version, String versionLabel, LocalDate startDate,
                      LocalDate endDate, long durationDays, String status, boolean current,
                      Long documentId, String documentName, String documentMimeType, Long documentSize,
                      String notes, Long addedBy, String addedByName, LocalDateTime addedAt,
                      Long updatedBy, LocalDateTime updatedAt) {}

    private final JdbcTemplate jdbc;
    private final BusinessRecordRepository records;
    private final FileStorageService storage;
    private final CurrentUserService users;
    private final DataScopeService scopes;

    public List<Sow> list(Long projectId) {
        project(projectId);
        return jdbc.query("""
                SELECT sow.id,sow.staffing_project_id,sow.sow_version,sow.start_date,sow.end_date,
                       sow.status,sow.current_sow,sow.document_id,document.original_file_name,
                       document.mime_type,document.file_size,sow.notes,sow.created_by,
                       COALESCE(resource.party_name,user.name,user.email,'System'),sow.created_at,
                       sow.updated_by,sow.updated_at
                FROM staffing_project_sow sow
                LEFT JOIN project_documents document ON document.id=sow.document_id
                LEFT JOIN app_users user ON user.id=sow.created_by
                LEFT JOIN business_records resource ON resource.id=user.resource_id
                WHERE sow.staffing_project_id=?
                ORDER BY sow.sow_version DESC,sow.id DESC
                """, (rs, row) -> map(rs), projectId);
    }

    public Sow current(Long projectId) {
        return list(projectId).stream().filter(Sow::current).findFirst().orElse(null);
    }

    @Transactional
    public Sow initialize(Long projectId, LocalDate startDate, LocalDate endDate, MultipartFile document, String notes) {
        BusinessRecord project = project(projectId);
        validateDates(startDate, endDate);
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM staffing_project_sow WHERE staffing_project_id=?", Integer.class, projectId);
        if (count != null && count > 0) throw new IllegalArgumentException("This Staffing Project already has an SOW. Use Renew SOW.");
        Long documentId = saveDocument(projectId, 1, document);
        long userId = users.getCurrentUserId();
        jdbc.update("""
                INSERT INTO staffing_project_sow(staffing_project_id,sow_version,start_date,end_date,document_id,notes,status,current_sow,created_by,updated_by)
                VALUES(?,1,?,?,?,?, 'ACTIVE',TRUE,?,?)
                """, projectId, startDate, endDate, documentId, clean(notes), userId, userId);
        syncCurrentDates(project, startDate, endDate);
        return current(projectId);
    }

    @Transactional
    public Sow updateCurrent(Long projectId, LocalDate startDate, LocalDate endDate, MultipartFile document, String notes) {
        BusinessRecord project = project(projectId);
        validateDates(startDate, endDate);
        Sow existing = current(projectId);
        if (existing == null) {
            if (document == null || document.isEmpty()) throw new IllegalArgumentException("Signed SOW Document is required.");
            return initialize(projectId, startDate, endDate, document, notes);
        }
        Long documentId = existing.documentId();
        if (document != null && !document.isEmpty()) documentId = saveDocument(projectId, existing.version(), document);
        jdbc.update("UPDATE staffing_project_sow SET start_date=?,end_date=?,document_id=?,notes=?,updated_by=? WHERE id=?",
                startDate, endDate, documentId, clean(notes), users.getCurrentUserId(), existing.id());
        syncCurrentDates(project, startDate, endDate);
        return current(projectId);
    }

    @Transactional
    public Sow renew(Long projectId, LocalDate startDate, LocalDate endDate, MultipartFile document, String notes) {
        BusinessRecord project = projectForUpdate(projectId);
        validateDates(startDate, endDate);
        Sow previous = current(projectId);
        if (previous == null) throw new IllegalArgumentException("Create the current SOW before renewing it.");
        if (!startDate.isAfter(previous.endDate())) {
            throw new IllegalArgumentException("New SOW Start Date must be after the current SOW End Date (" + previous.endDate() + ").");
        }
        int version = previous.version() + 1;
        Long documentId = saveDocument(projectId, version, document);
        long userId = users.getCurrentUserId();
        jdbc.update("UPDATE staffing_project_sow SET current_sow=FALSE,status='SUPERSEDED',updated_by=? WHERE id=?", userId, previous.id());
        jdbc.update("""
                INSERT INTO staffing_project_sow(staffing_project_id,sow_version,start_date,end_date,document_id,notes,status,current_sow,created_by,updated_by)
                VALUES(?,?,?,?,?,?, 'ACTIVE',TRUE,?,?)
                """, projectId, version, startDate, endDate, documentId, clean(notes), userId, userId);
        syncCurrentDates(project, startDate, endDate);
        return current(projectId);
    }

    private Long saveDocument(Long projectId, int version, MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Signed SOW Document is required.");
        String extension = extension(file.getOriginalFilename());
        if (!List.of("PDF", "DOC", "DOCX").contains(extension)) {
            throw new IllegalArgumentException("Signed SOW must be a PDF, DOC or DOCX file.");
        }
        FileUploadResponse uploaded = storage.uploadProjectDocument(file, "staffing", projectId);
        jdbc.update("""
                INSERT INTO project_documents(project_id,project_type,document_type,sow_version,original_file_name,
                    storage_key,file_extension,mime_type,file_size,uploaded_by)
                VALUES(?,'staffing','SOW',?,?,?,?,?,?,?)
                """, projectId, version, uploaded.fileName(), uploaded.key(), extension,
                uploaded.contentType(), uploaded.size(), users.getCurrentUserId());
        return jdbc.queryForObject("SELECT MAX(id) FROM project_documents WHERE project_id=? AND project_type='staffing' AND document_type='SOW' AND sow_version=?",
                Long.class, projectId, version);
    }

    private void validateDates(LocalDate startDate, LocalDate endDate) {
        if (startDate == null) throw new IllegalArgumentException("SOW Start Date is required.");
        if (endDate == null) throw new IllegalArgumentException("SOW End Date is required.");
        if (endDate.isBefore(startDate)) throw new IllegalArgumentException("SOW End Date cannot be earlier than SOW Start Date.");
    }

    private BusinessRecord project(Long projectId) {
        BusinessRecord project = records.findByModuleAndTypeAndId("projects", "staffing", projectId)
                .orElseThrow(() -> new IllegalArgumentException("Staffing Project does not exist."));
        scopes.validateRecordAccess(project.getCreatedBy());
        return project;
    }

    private BusinessRecord projectForUpdate(Long projectId) {
        project(projectId);
        return records.findStaffingProjectForUpdate(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Staffing Project does not exist."));
    }

    private void syncCurrentDates(BusinessRecord project, LocalDate startDate, LocalDate endDate) {
        project.setRecordDate(startDate);
        project.setDueDate(endDate);
        records.save(project);
    }

    private Sow map(java.sql.ResultSet rs) throws java.sql.SQLException {
        LocalDate start = rs.getDate(4).toLocalDate();
        LocalDate end = rs.getDate(5).toLocalDate();
        boolean current = rs.getBoolean(7);
        Long documentId = nullableLong(rs, 8);
        LocalDate today = LocalDate.now();
        String displayStatus = end.isBefore(today) ? "EXPIRED"
                : current && !end.isAfter(today.plusDays(30)) ? "EXPIRING_SOON"
                : current ? "ACTIVE" : rs.getString(6);
        return new Sow(rs.getLong(1), rs.getLong(2), rs.getInt(3), "SOW-%02d".formatted(rs.getInt(3)),
                start, end, ChronoUnit.DAYS.between(start, end) + 1, displayStatus, current,
                documentId, rs.getString(9), rs.getString(10), nullableLong(rs, 11), rs.getString(12),
                nullableLong(rs, 13), rs.getString(14), rs.getTimestamp(15).toLocalDateTime(),
                nullableLong(rs, 16), rs.getTimestamp(17).toLocalDateTime());
    }

    private Long nullableLong(java.sql.ResultSet rs, int column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
    private String extension(String name) { int index=name==null?-1:name.lastIndexOf('.'); return index<0?"":name.substring(index+1).toUpperCase(Locale.ROOT); }
    private String clean(String value) { return StringUtils.hasText(value) ? value.trim() : null; }
}
