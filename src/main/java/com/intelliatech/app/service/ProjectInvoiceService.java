package com.intelliatech.app.service;

import com.intelliatech.app.entity.BusinessRecord;
import com.intelliatech.app.repository.BusinessRecordRepository;
import com.intelliatech.app.security.CurrentUserService;
import com.intelliatech.app.security.DataScopeService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProjectInvoiceService {
    public record ProjectOption(Long id, String projectType, String name, String code, String customer,
                                String currency, String status) {}
    public record ProjectInvoice(Long id, String invoiceNumber, LocalDate invoiceDate, LocalDate dueDate,
                                 String status, BigDecimal amount, BigDecimal paid, BigDecimal balance,
                                 String currency) {}
    public record ProjectInvoices(List<ProjectInvoice> invoices, BigDecimal totalBilled,
                                  BigDecimal totalPaid, BigDecimal totalBalance) {}

    private final JdbcTemplate jdbc;
    private final BusinessRecordRepository records;
    private final CurrentUserService users;
    private final DataScopeService scopes;

    public List<ProjectOption> lookup(String projectType, String customer) {
        String type = normalizedType(projectType);
        var current = users.getCurrentUser();
        String access = users.isAdmin(current) ? "" : " AND br.created_by=" + current.getId();
        String customerColumn = "fixedCost".equals(type) ? "br.party_city" : "br.party_name";
        String sql = """
                SELECT br.id,br.type,
                       CASE WHEN br.type='staffing'
                            THEN COALESCE(NULLIF(TRIM(br.owner_name),''),br.record_number)
                            ELSE br.party_name END project_name,
                       br.record_number,
                       CASE WHEN br.type='fixedCost' THEN br.party_city ELSE br.party_name END customer,
                       UPPER(LEFT(COALESCE(br.payment_mode,'INR'),3)) currency,br.status
                FROM business_records br
                WHERE br.module='projects' AND br.type=?
                  AND UPPER(br.status) NOT IN ('CANCELLED','DELETED','ARCHIVED')
                """ + (customer == null || customer.isBlank() ? "" : " AND LOWER(TRIM(" + customerColumn + "))=LOWER(TRIM(?))")
                + access + " ORDER BY br.party_name,br.record_number";
        Object[] args = customer == null || customer.isBlank() ? new Object[]{type} : new Object[]{type, customer};
        return jdbc.query(sql, (rs, row) -> new ProjectOption(rs.getLong(1), rs.getString(2), rs.getString(3),
                rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7)), args);
    }

    public ProjectInvoices invoices(String projectType, Long projectId) {
        BusinessRecord project = project(projectType, projectId);
        List<ProjectInvoice> rows = jdbc.query("""
                SELECT i.id,i.record_number,i.record_date,i.due_date,
                       COALESCE(l.lifecycle_status,i.status),i.amount,
                       COALESCE(l.cash_amount_paid,0)+COALESCE(l.tds_settled,0)+COALESCE(l.credit_applied,0)+COALESCE(l.credit_note_applied,0) paid,
                       COALESCE(l.balance_due,i.balance_amount),
                       UPPER(LEFT(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(i.notes,'$.Currency')),p.payment_mode,'INR'),3)) currency
                FROM invoice_project_links link
                JOIN business_records i ON i.id=link.invoice_id AND i.module='sales' AND i.type='invoices'
                JOIN business_records p ON p.id=link.project_id
                LEFT JOIN invoice_lifecycles l ON l.invoice_id=i.id
                WHERE link.project_id=? AND link.project_type=?
                ORDER BY i.record_date DESC,i.id DESC
                """, (rs, row) -> new ProjectInvoice(rs.getLong(1), rs.getString(2), rs.getObject(3, LocalDate.class),
                rs.getObject(4, LocalDate.class), rs.getString(5), rs.getBigDecimal(6), rs.getBigDecimal(7),
                rs.getBigDecimal(8), rs.getString(9)), projectId, normalizedType(projectType));
        BigDecimal billed = rows.stream().filter(row -> !"VOID".equalsIgnoreCase(row.status()) && !"DRAFT".equalsIgnoreCase(row.status()))
                .map(ProjectInvoice::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paid = rows.stream().map(ProjectInvoice::paid).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal balance = rows.stream().map(ProjectInvoice::balance).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ProjectInvoices(rows, billed, paid, balance);
    }

    public BusinessRecord project(String projectType, Long projectId) {
        String type = normalizedType(projectType);
        BusinessRecord project = records.findByModuleAndTypeAndId("projects", type, projectId)
                .orElseThrow(() -> new IllegalArgumentException("Selected project does not exist."));
        scopes.validateRecordAccess(project.getCreatedBy());
        return project;
    }

    public String normalizedType(String value) {
        if ("fixedCost".equals(value) || "staffing".equals(value)) return value;
        throw new IllegalArgumentException("Invoice Type must be Fixed Cost or Staffing.");
    }
}
