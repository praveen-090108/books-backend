package com.intelliatech.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.intelliatech.app.entity.BusinessRecord;
import com.intelliatech.app.repository.BusinessRecordRepository;
import com.intelliatech.app.security.CurrentUserService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ResourceLifecycleService {
    private static final List<String> EMPLOYMENT_STATUSES = List.of("ACTIVE", "SERVING_NOTICE", "RELIEVED", "INACTIVE");
    private static final List<String> RELEASE_STATUSES = List.of("TENTATIVE", "CONFIRMED", "CANCELLED", "RELEASED");

    private final JdbcTemplate jdbc;
    private final BusinessRecordRepository records;
    private final CurrentUserService currentUsers;
    private final ObjectMapper json;

    public record SalaryRevisionRequest(BigDecimal revisedSalary, String salaryCurrency, LocalDate effectiveDate,
                                        String reason, String remarks) {}
    public record SalaryRevision(Long id, Long resourceId, BigDecimal previousSalary, BigDecimal revisedSalary,
                                 String salaryCurrency, LocalDate effectiveDate, BigDecimal revisionAmount,
                                 BigDecimal revisionPercentage, String reason, String remarks,
                                 Long updatedBy, String updatedByName, LocalDateTime updatedAt) {}
    public record EmploymentRequest(String employmentStatus, LocalDate resignationDate, LocalDate noticeStartDate,
                                    LocalDate lastWorkingDay, String reason, String remarks,
                                    boolean allowEarlyResignation) {}
    public record EmploymentEntry(Long id, String employmentStatus, LocalDate resignationDate,
                                  LocalDate noticeStartDate, LocalDate lastWorkingDay, String reason,
                                  String remarks, boolean earlyResignationOverride, Long updatedBy,
                                  String updatedByName, LocalDateTime updatedAt) {}
    public record AllocationEntry(Long id, Long resourceId, String resourceName, String employmentStatus,
                                  LocalDate lastWorkingDay, Long projectId, String projectName, String projectType,
                                  String client, String roleDesignation, LocalDate allocationStartDate,
                                  LocalDate plannedReleaseDate, LocalDate actualReleaseDate, LocalDate lastBillingDate,
                                  String allocationStatus, BigDecimal allocationPercentage, BigDecimal monthlyBilling,
                                  String billingCurrency, boolean billable, String confirmationStatus,
                                  String releaseReason, String remarks) {}
    public record ReleaseRequest(LocalDate expectedReleaseDate, LocalDate notificationDate,
                                 String confirmationStatus, String reason, String remarks) {}
    public record CloseAllocationRequest(LocalDate actualReleaseDate, LocalDate lastBillingDate,
                                         String reason, String remarks) {}
    public record Availability(Long resourceId, String resourceName, String employmentStatus,
                               LocalDate lastWorkingDay, long noticeDaysRemaining, String availability,
                               int activeAllocations, BigDecimal allocatedPercentage,
                               LocalDate plannedReleaseDate, long releaseDaysRemaining) {}
    public record AvailabilitySummary(long totalResources, long currentlyAllocated, long available,
                                      long releasingSoon, long servingNotice, List<Availability> resources) {}
    public record BillingSummary(Long projectId, int activeResources, BigDecimal currentMonthlyBilling,
                                 String currency, int upcomingReleases, int closedResources) {}
    public record DashboardSummary(long totalResources, long availableNow, long currentlyAllocated,
                                   long releasingSoon, long servingNotice, long partiallyAvailable,
                                   long fullyAllocated, long overallocated) {}
    public record DashboardResource(Long resourceId, String resourceName, String employeeCode,
                                    String designation, String department, String skills,
                                    String reportingManager, String employmentStatus,
                                    LocalDate resignationDate, LocalDate noticeStartDate,
                                    LocalDate lastWorkingDay, long noticeDaysRemaining,
                                    int activeAllocations, BigDecimal allocatedPercentage,
                                    BigDecimal availablePercentage, String availability,
                                    String customers, String projects, String projectTypes,
                                    LocalDate expectedRelease, LocalDate availableFrom) {}
    public record ForecastPoint(int days, LocalDate date, long confirmedResources,
                                BigDecimal confirmedCapacity, long tentativeResources,
                                BigDecimal tentativeCapacity) {}
    public record AvailabilityDashboard(DashboardSummary summary, List<DashboardResource> resources,
                                        List<AllocationEntry> upcomingReleases,
                                        List<ForecastPoint> forecast) {}

    @Transactional
    public void initializeResource(BusinessRecord resource) {
        if (!"resources".equalsIgnoreCase(resource.getModule()) || !"resources".equalsIgnoreCase(resource.getType())) return;
        BigDecimal salary = salaryFromMaster(resource);
        String currency = cleanCurrency(null, resource);
        Integer salaryRows = jdbc.queryForObject("SELECT COUNT(*) FROM resource_salary_history WHERE resource_id=?", Integer.class, resource.getId());
        if (salary.signum() > 0 && salaryRows != null && salaryRows == 0) {
            jdbc.update("""
                    INSERT INTO resource_salary_history(resource_id,previous_salary,revised_salary,salary_currency,
                    effective_date,revision_amount,revision_percentage,revision_reason,remarks,updated_by)
                    VALUES(?,?,?,?,?,0,0,'INITIAL_SALARY','Initial salary recorded when the resource was created.',?)
                    """, resource.getId(), salary, salary, currency, resource.getRecordDate(), currentUsers.getCurrentUserId());
        }
        if (resource.getEmploymentStatus() == null) {
            resource.setEmploymentStatus("Active".equalsIgnoreCase(resource.getStatus()) ? "ACTIVE" : "INACTIVE");
            records.save(resource);
        }
        Integer employmentRows = jdbc.queryForObject("SELECT COUNT(*) FROM resource_employment_history WHERE resource_id=?", Integer.class, resource.getId());
        if (employmentRows != null && employmentRows == 0) {
            jdbc.update("INSERT INTO resource_employment_history(resource_id,employment_status,reason,remarks,updated_by) VALUES(?,?,'INITIAL_STATUS','Initial employment status recorded when the resource was created.',?)",
                    resource.getId(), resource.getEmploymentStatus(), currentUsers.getCurrentUserId());
        }
    }

    public void assertSalaryUnchanged(BusinessRecord resource, String requestedNotes) {
        try {
            BigDecimal current = salaryFromMaster(resource);
            BigDecimal requested = json.readTree(requestedNotes == null ? "{}" : requestedNotes).path("monthlySalary").decimalValue();
            if (current.compareTo(requested) != 0) {
                throw new IllegalArgumentException("Use Update Salary to create an effective-dated Salary Revision.");
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Monthly Salary is invalid.");
        }
    }

    @Transactional(readOnly = true)
    public List<SalaryRevision> salaryHistory(Long resourceId) {
        resource(resourceId, false);
        return jdbc.query("""
                SELECT h.*,u.name updated_by_name FROM resource_salary_history h
                LEFT JOIN app_users u ON u.id=h.updated_by WHERE h.resource_id=?
                ORDER BY h.effective_date DESC,h.id DESC
                """, (rs, row) -> salary(rs), resourceId);
    }

    @Transactional
    public SalaryRevision reviseSalary(Long resourceId, SalaryRevisionRequest request) {
        BusinessRecord resource = resource(resourceId, true);
        if (request.revisedSalary() == null || request.revisedSalary().signum() <= 0) {
            throw new IllegalArgumentException("Revised Salary must be greater than zero.");
        }
        if (request.effectiveDate() == null) throw new IllegalArgumentException("Effective Date is required.");
        if (request.reason() == null || request.reason().isBlank()) throw new IllegalArgumentException("Revision Reason is required.");
        LocalDate latestRevisionDate = latestEffectiveDate(resourceId, LocalDate.of(9999, 12, 31));
        if (latestRevisionDate != null && !request.effectiveDate().isAfter(latestRevisionDate)) {
            throw new IllegalArgumentException("Effective Date must be after the latest Salary Revision date " + latestRevisionDate + ".");
        }
        Integer duplicate = jdbc.queryForObject("SELECT COUNT(*) FROM resource_salary_history WHERE resource_id=? AND effective_date=?",
                Integer.class, resourceId, request.effectiveDate());
        if (duplicate != null && duplicate > 0) throw new IllegalArgumentException("A Salary Revision already exists for this Effective Date.");
        BigDecimal previous = salaryAt(resourceId, request.effectiveDate().minusDays(1));
        if (previous == null) previous = salaryFromMaster(resource);
        BigDecimal change = request.revisedSalary().subtract(previous);
        BigDecimal percentage = previous.signum() == 0 ? BigDecimal.ZERO
                : change.multiply(BigDecimal.valueOf(100)).divide(previous, 4, RoundingMode.HALF_UP);
        String currency = cleanCurrency(request.salaryCurrency(), resource);
        jdbc.update("""
                INSERT INTO resource_salary_history(resource_id,previous_salary,revised_salary,salary_currency,
                effective_date,revision_amount,revision_percentage,revision_reason,remarks,updated_by)
                VALUES(?,?,?,?,?,?,?,?,?,?)
                """, resourceId, previous, request.revisedSalary(), currency, request.effectiveDate(), change,
                percentage, request.reason().trim(), clean(request.remarks()), currentUsers.getCurrentUserId());
        if (!request.effectiveDate().isAfter(LocalDate.now())
                && request.effectiveDate().equals(latestEffectiveDate(resourceId, LocalDate.now()))) {
            updateMasterSalary(resource, request.revisedSalary(), currency);
        }
        return salaryHistory(resourceId).stream()
                .filter(entry -> request.effectiveDate().equals(entry.effectiveDate()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("The Salary Revision was saved but could not be reloaded."));
    }

    @Transactional
    public boolean closeProjectAllocations(Long projectId, LocalDate actualReleaseDate, String reason) {
        Integer allocationCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM project_resource_allocation WHERE project_id=?", Integer.class, projectId);
        if (allocationCount == null || allocationCount == 0) return false;
        jdbc.update("""
                UPDATE project_resource_allocation
                SET allocation_status=CASE WHEN project_type='STAFFING' THEN 'CLOSED' ELSE 'COMPLETED' END,
                    actual_release_date=COALESCE(actual_release_date,?),
                    last_billing_date=CASE WHEN billable=TRUE THEN COALESCE(last_billing_date,?) ELSE last_billing_date END,
                    release_reason=COALESCE(release_reason,?), updated_by=?
                WHERE project_id=? AND allocation_status IN ('ACTIVE','PLANNED_RELEASE')
                """, actualReleaseDate, actualReleaseDate, clean(reason), currentUsers.getCurrentUserId(), projectId);
        return true;
    }

    @Transactional(readOnly = true)
    public List<EmploymentEntry> employmentHistory(Long resourceId) {
        resource(resourceId, false);
        return jdbc.query("""
                SELECT h.*,u.name updated_by_name FROM resource_employment_history h
                LEFT JOIN app_users u ON u.id=h.updated_by WHERE h.resource_id=? ORDER BY h.updated_at DESC,h.id DESC
                """, (rs, row) -> new EmploymentEntry(rs.getLong("id"), rs.getString("employment_status"),
                rs.getObject("resignation_date", LocalDate.class), rs.getObject("notice_start_date", LocalDate.class),
                rs.getObject("last_working_day", LocalDate.class), rs.getString("reason"), rs.getString("remarks"),
                rs.getBoolean("early_resignation_override"), nullableLong(rs, "updated_by"),
                rs.getString("updated_by_name"), rs.getObject("updated_at", LocalDateTime.class)), resourceId);
    }

    @Transactional
    public EmploymentEntry updateEmployment(Long resourceId, EmploymentRequest request) {
        BusinessRecord resource = resource(resourceId, true);
        String status = normalized(request.employmentStatus());
        if (!EMPLOYMENT_STATUSES.contains(status)) throw new IllegalArgumentException("Employment Status must be Active, Serving Notice, Relieved or Inactive.");
        if ("SERVING_NOTICE".equals(status)) {
            if (request.resignationDate() == null || request.noticeStartDate() == null || request.lastWorkingDay() == null) {
                throw new IllegalArgumentException("Resignation Date, Notice Start Date and Last Working Day are required while Serving Notice.");
            }
            if (request.lastWorkingDay().isBefore(request.noticeStartDate())) {
                throw new IllegalArgumentException("Last Working Day cannot be before Notice Start Date.");
            }
            LocalDate earliestAllowed = jdbc.queryForObject("""
                    SELECT MAX(DATE_ADD(allocation_start_date, INTERVAL 6 MONTH))
                    FROM project_resource_allocation
                    WHERE resource_id=? AND allocation_status IN ('ACTIVE','PLANNED_RELEASE')
                    """, LocalDate.class, resourceId);
            if (earliestAllowed != null && request.resignationDate().isBefore(earliestAllowed)
                    && !request.allowEarlyResignation()) {
                throw new IllegalArgumentException("Cannot resign before completing 6 months on the active project. "
                        + "The earliest allowed resignation date is " + earliestAllowed
                        + ". Select the early resignation override to continue.");
            }
        }
        resource.setEmploymentStatus(status);
        resource.setResignationDate(request.resignationDate());
        resource.setNoticeStartDate(request.noticeStartDate());
        resource.setLastWorkingDay(request.lastWorkingDay());
        resource.setResignationReason(clean(request.reason()));
        resource.setEmploymentRemarks(clean(request.remarks()));
        resource.setUpdatedBy(currentUsers.getCurrentUserId());
        records.save(resource);
        jdbc.update("""
                INSERT INTO resource_employment_history(resource_id,employment_status,resignation_date,
                notice_start_date,last_working_day,reason,remarks,early_resignation_override,updated_by)
                VALUES(?,?,?,?,?,?,?,?,?)
                """, resourceId, status, request.resignationDate(), request.noticeStartDate(), request.lastWorkingDay(),
                clean(request.reason()), clean(request.remarks()), request.allowEarlyResignation(),
                currentUsers.getCurrentUserId());
        return employmentHistory(resourceId).get(0);
    }

    @Transactional(readOnly = true)
    public List<AllocationEntry> projectHistory(Long resourceId) {
        resource(resourceId, false);
        return allocationRows("WHERE a.resource_id=? ORDER BY a.allocation_start_date DESC,a.id DESC", resourceId);
    }

    @Transactional(readOnly = true)
    public List<AllocationEntry> projectAllocations(Long projectId) {
        project(projectId);
        return allocationRows("WHERE a.project_id=? ORDER BY CASE a.allocation_status WHEN 'ACTIVE' THEN 0 WHEN 'PLANNED_RELEASE' THEN 1 ELSE 2 END,a.allocation_start_date", projectId);
    }

    @Transactional
    public AllocationEntry scheduleRelease(Long projectId, Long allocationId, ReleaseRequest request) {
        project(projectId);
        AllocationEntry current = allocation(projectId, allocationId);
        String status = normalized(request.confirmationStatus());
        if (!RELEASE_STATUSES.contains(status)) throw new IllegalArgumentException("Release Status must be Tentative, Confirmed, Cancelled or Released.");
        if (!"CANCELLED".equals(status) && request.expectedReleaseDate() == null) throw new IllegalArgumentException("Expected Release Date is required.");
        if (request.expectedReleaseDate() != null && request.expectedReleaseDate().isBefore(current.allocationStartDate())) {
            throw new IllegalArgumentException("Expected Release Date cannot be before Allocation Start Date.");
        }
        jdbc.update("""
                INSERT INTO project_release_history(allocation_id,previous_release_date,new_release_date,
                previous_confirmation_status,new_confirmation_status,reason,remarks,changed_by) VALUES(?,?,?,?,?,?,?,?)
                """, allocationId, current.plannedReleaseDate(), "CANCELLED".equals(status) ? null : request.expectedReleaseDate(),
                current.confirmationStatus(), status, clean(request.reason()), clean(request.remarks()), currentUsers.getCurrentUserId());
        jdbc.update("""
                UPDATE project_resource_allocation SET planned_release_date=?,release_notification_date=?,
                release_confirmation_status=?,allocation_status=?,release_reason=?,remarks=?,updated_by=? WHERE id=? AND project_id=?
                """, "CANCELLED".equals(status) ? null : request.expectedReleaseDate(), request.notificationDate(), status,
                "CANCELLED".equals(status) ? "ACTIVE" : "RELEASED".equals(status) ? "RELEASED" : "PLANNED_RELEASE",
                clean(request.reason()), clean(request.remarks()), currentUsers.getCurrentUserId(), allocationId, projectId);
        return allocation(projectId, allocationId);
    }

    @Transactional
    public AllocationEntry closeAllocation(Long projectId, Long allocationId, CloseAllocationRequest request) {
        project(projectId);
        AllocationEntry current = allocation(projectId, allocationId);
        if (request.actualReleaseDate() == null) throw new IllegalArgumentException("Actual Release Date is required.");
        if (request.actualReleaseDate().isBefore(current.allocationStartDate())) throw new IllegalArgumentException("Actual Release Date cannot be before Allocation Start Date.");
        if (current.billable() && request.lastBillingDate() == null) throw new IllegalArgumentException("Actual Last Billing Date is required for a billable allocation.");
        jdbc.update("""
                INSERT INTO project_release_history(allocation_id,previous_release_date,new_release_date,
                previous_confirmation_status,new_confirmation_status,reason,remarks,changed_by) VALUES(?,?,?,?,?,?,?,?)
                """, allocationId, current.plannedReleaseDate(), request.actualReleaseDate(), current.confirmationStatus(),
                "RELEASED", clean(request.reason()), clean(request.remarks()), currentUsers.getCurrentUserId());
        jdbc.update("""
                UPDATE project_resource_allocation SET actual_release_date=?,last_billing_date=?,allocation_status='RELEASED',
                release_confirmation_status='RELEASED',release_reason=?,remarks=?,updated_by=? WHERE id=? AND project_id=?
                """, request.actualReleaseDate(), request.lastBillingDate(), clean(request.reason()), clean(request.remarks()),
                currentUsers.getCurrentUserId(), allocationId, projectId);
        return allocation(projectId, allocationId);
    }

    @Transactional(readOnly = true)
    public AvailabilitySummary availability() {
        LocalDate today = LocalDate.now();
        List<Availability> rows = jdbc.query("""
                SELECT r.id,r.party_name,COALESCE(r.employment_status,'ACTIVE') employment_status,r.last_working_day,
                COUNT(CASE WHEN a.allocation_status IN ('ACTIVE','PLANNED_RELEASE') AND (a.actual_release_date IS NULL OR a.actual_release_date>=?) THEN 1 END) active_count,
                COALESCE(SUM(CASE WHEN a.allocation_status IN ('ACTIVE','PLANNED_RELEASE') AND (a.actual_release_date IS NULL OR a.actual_release_date>=?) THEN a.allocation_percentage ELSE 0 END),0) allocated_percentage,
                MIN(CASE WHEN a.release_confirmation_status IN ('TENTATIVE','CONFIRMED') AND a.planned_release_date>=? THEN a.planned_release_date END) planned_release
                FROM business_records r LEFT JOIN project_resource_allocation a ON a.resource_id=r.id
                WHERE r.module='resources' AND r.type='resources'
                GROUP BY r.id,r.party_name,r.employment_status,r.last_working_day ORDER BY r.party_name
                """, (rs, row) -> availability(rs, today), today, today, today);
        return new AvailabilitySummary(rows.size(), rows.stream().filter(x -> x.activeAllocations() > 0).count(),
                rows.stream().filter(x -> "AVAILABLE".equals(x.availability())).count(),
                rows.stream().filter(x -> "RELEASING_SOON".equals(x.availability())).count(),
                rows.stream().filter(x -> "SERVING_NOTICE".equals(x.employmentStatus())).count(), rows);
    }

    @Transactional(readOnly = true)
    public AvailabilityDashboard availabilityDashboard(int releaseDays) {
        LocalDate today = LocalDate.now();
        LocalDate releaseThrough = today.plusDays(Math.max(1, releaseDays));
        List<DashboardResource> resources = dashboardResources(today);
        List<AllocationEntry> upcoming = allocationRows("""
                WHERE a.allocation_status IN ('ACTIVE','PLANNED_RELEASE')
                  AND a.release_confirmation_status IN ('TENTATIVE','CONFIRMED')
                  AND a.planned_release_date BETWEEN ? AND ?
                ORDER BY a.planned_release_date,a.resource_id,a.id
                """, today, releaseThrough);
        DashboardSummary summary = new DashboardSummary(
                resources.stream().filter(this::currentlyEmployed).count(),
                resources.stream().filter(x -> currentlyEmployed(x) && x.allocatedPercentage().signum() == 0).count(),
                resources.stream().filter(x -> currentlyEmployed(x) && x.activeAllocations() > 0).count(),
                resources.stream().filter(x -> currentlyEmployed(x) && x.expectedRelease() != null
                        && !x.expectedRelease().isAfter(releaseThrough)).count(),
                resources.stream().filter(x -> "SERVING_NOTICE".equals(x.employmentStatus())).count(),
                resources.stream().filter(x -> currentlyEmployed(x) && x.allocatedPercentage().signum() > 0
                        && x.allocatedPercentage().compareTo(BigDecimal.valueOf(100)) < 0).count(),
                resources.stream().filter(x -> currentlyEmployed(x) && x.allocatedPercentage().compareTo(BigDecimal.valueOf(100)) == 0).count(),
                resources.stream().filter(x -> currentlyEmployed(x) && x.allocatedPercentage().compareTo(BigDecimal.valueOf(100)) > 0).count());
        return new AvailabilityDashboard(summary, resources, upcoming, forecast(today));
    }

    private List<DashboardResource> dashboardResources(LocalDate today) {
        return jdbc.query("""
                SELECT r.id,r.party_name,r.record_number,COALESCE(r.designation,r.category) designation,
                r.department,JSON_UNQUOTE(JSON_EXTRACT(r.notes,'$.skills')) skills,
                manager.party_name reporting_manager,COALESCE(r.employment_status,'ACTIVE') employment_status,
                r.resignation_date,r.notice_start_date,r.last_working_day,
                COUNT(CASE WHEN a.allocation_status IN ('ACTIVE','PLANNED_RELEASE')
                     AND a.allocation_start_date<=? AND (a.actual_release_date IS NULL OR a.actual_release_date>=?) THEN 1 END) active_count,
                COALESCE(SUM(CASE WHEN a.allocation_status IN ('ACTIVE','PLANNED_RELEASE')
                     AND a.allocation_start_date<=? AND (a.actual_release_date IS NULL OR a.actual_release_date>=?)
                     THEN a.allocation_percentage ELSE 0 END),0) allocated_percentage,
                GROUP_CONCAT(DISTINCT CASE WHEN a.allocation_status IN ('ACTIVE','PLANNED_RELEASE')
                     AND a.allocation_start_date<=? AND (a.actual_release_date IS NULL OR a.actual_release_date>=?)
                     THEN p.party_name END ORDER BY p.party_name SEPARATOR ', ') customers,
                GROUP_CONCAT(DISTINCT CASE WHEN a.allocation_status IN ('ACTIVE','PLANNED_RELEASE')
                     AND a.allocation_start_date<=? AND (a.actual_release_date IS NULL OR a.actual_release_date>=?)
                     THEN COALESCE(p.record_number,p.party_name) END ORDER BY p.record_number SEPARATOR ', ') projects,
                GROUP_CONCAT(DISTINCT CASE WHEN a.allocation_status IN ('ACTIVE','PLANNED_RELEASE')
                     AND a.allocation_start_date<=? AND (a.actual_release_date IS NULL OR a.actual_release_date>=?)
                     THEN a.project_type END ORDER BY a.project_type SEPARATOR ', ') project_types,
                MIN(CASE WHEN a.release_confirmation_status IN ('TENTATIVE','CONFIRMED')
                     AND a.planned_release_date>=? THEN a.planned_release_date END) expected_release,
                MAX(CASE WHEN a.allocation_status IN ('RELEASED','COMPLETED','CLOSED') THEN a.actual_release_date END) last_release
                FROM business_records r
                LEFT JOIN business_records manager ON manager.id=r.reporting_manager_id
                LEFT JOIN project_resource_allocation a ON a.resource_id=r.id
                LEFT JOIN business_records p ON p.id=a.project_id
                WHERE r.module='resources' AND r.type='resources'
                GROUP BY r.id,r.party_name,r.record_number,r.designation,r.category,r.department,r.notes,
                         manager.party_name,r.employment_status,r.resignation_date,r.notice_start_date,r.last_working_day
                ORDER BY r.party_name
                """, (rs, row) -> {
            BigDecimal allocated = rs.getBigDecimal("allocated_percentage");
            if (allocated == null) allocated = BigDecimal.ZERO;
            BigDecimal available = BigDecimal.valueOf(100).subtract(allocated).max(BigDecimal.ZERO);
            String employment = rs.getString("employment_status");
            LocalDate lastWorkingDay = rs.getObject("last_working_day", LocalDate.class);
            LocalDate expected = rs.getObject("expected_release", LocalDate.class);
            LocalDate lastRelease = rs.getObject("last_release", LocalDate.class);
            String state = allocated.signum() == 0 ? "AVAILABLE" : allocated.compareTo(BigDecimal.valueOf(100)) < 0
                    ? "PARTIALLY_AVAILABLE" : allocated.compareTo(BigDecimal.valueOf(100)) == 0 ? "ALLOCATED" : "OVERALLOCATED";
            return new DashboardResource(rs.getLong("id"), rs.getString("party_name"), rs.getString("record_number"),
                    rs.getString("designation"), rs.getString("department"), rs.getString("skills"),
                    rs.getString("reporting_manager"), employment, rs.getObject("resignation_date", LocalDate.class),
                    rs.getObject("notice_start_date", LocalDate.class), lastWorkingDay,
                    lastWorkingDay == null ? -1 : ChronoUnit.DAYS.between(today, lastWorkingDay),
                    rs.getInt("active_count"), allocated, available, state, rs.getString("customers"),
                    rs.getString("projects"), rs.getString("project_types"), expected,
                    allocated.signum() == 0 ? (lastRelease == null ? today : lastRelease.plusDays(1)) : expected);
        }, today, today, today, today, today, today, today, today, today, today, today);
    }

    private List<ForecastPoint> forecast(LocalDate today) {
        List<ForecastPoint> points = new ArrayList<>();
        for (int days : List.of(7, 15, 30, 60, 90)) {
            LocalDate date = today.plusDays(days);
            List<BigDecimal> confirmed = projectedCapacity(date, false);
            List<BigDecimal> tentative = projectedCapacity(date, true);
            points.add(new ForecastPoint(days, date, confirmed.stream().filter(x -> x.signum() > 0).count(),
                    confirmed.stream().reduce(BigDecimal.ZERO, BigDecimal::add),
                    tentative.stream().filter(x -> x.signum() > 0).count(),
                    tentative.stream().reduce(BigDecimal.ZERO, BigDecimal::add)));
        }
        return points;
    }

    private List<BigDecimal> projectedCapacity(LocalDate date, boolean includeTentative) {
        return jdbc.query("""
                SELECT r.id, GREATEST(0,100-COALESCE(SUM(CASE
                  WHEN a.allocation_start_date<=? AND (a.actual_release_date IS NULL OR a.actual_release_date>?)
                   AND NOT (a.planned_release_date<=? AND (a.release_confirmation_status IN ('CONFIRMED','RELEASED')
                        OR (?=TRUE AND a.release_confirmation_status='TENTATIVE')))
                   AND a.allocation_status IN ('ACTIVE','PLANNED_RELEASE') THEN a.allocation_percentage ELSE 0 END),0)) capacity
                FROM business_records r LEFT JOIN project_resource_allocation a ON a.resource_id=r.id
                WHERE r.module='resources' AND r.type='resources'
                  AND COALESCE(r.employment_status,'ACTIVE') NOT IN ('RELIEVED','INACTIVE')
                  AND (r.last_working_day IS NULL OR r.last_working_day>?) GROUP BY r.id
                """, (rs, row) -> rs.getBigDecimal("capacity"), date, date, date, includeTentative, date);
    }

    private boolean currentlyEmployed(DashboardResource resource) {
        return !Set.of("RELIEVED", "INACTIVE").contains(resource.employmentStatus());
    }

    @Transactional(readOnly = true)
    public BillingSummary billingSummary(Long projectId) {
        BusinessRecord project = project(projectId);
        LocalDate today = LocalDate.now();
        return jdbc.queryForObject("""
                SELECT COUNT(CASE WHEN allocation_status IN ('ACTIVE','PLANNED_RELEASE') AND billable=TRUE
                     AND allocation_start_date<=? AND (actual_release_date IS NULL OR actual_release_date>=?) THEN 1 END) active_count,
                COALESCE(SUM(CASE WHEN allocation_status IN ('ACTIVE','PLANNED_RELEASE') AND billable=TRUE
                     AND allocation_start_date<=? AND (actual_release_date IS NULL OR actual_release_date>=?) THEN monthly_billing ELSE 0 END),0) billing,
                COUNT(CASE WHEN release_confirmation_status IN ('TENTATIVE','CONFIRMED') AND planned_release_date>=? THEN 1 END) upcoming,
                COUNT(CASE WHEN allocation_status IN ('RELEASED','COMPLETED','CLOSED') THEN 1 END) closed_count
                FROM project_resource_allocation WHERE project_id=?
                """, (rs, row) -> new BillingSummary(projectId,
                isClosed(project) ? 0 : rs.getInt("active_count"),
                isClosed(project) ? BigDecimal.ZERO : rs.getBigDecimal("billing"),
                project.getPaymentMode() == null ? "INR" : project.getPaymentMode().substring(0, Math.min(3, project.getPaymentMode().length())).toUpperCase(Locale.ROOT),
                rs.getInt("upcoming"), rs.getInt("closed_count")), today, today, today, today, today, projectId);
    }

    @Transactional
    public void synchronizeStaffingAllocation(BusinessRecord project) {
        if (!"projects".equalsIgnoreCase(project.getModule()) || !"staffing".equalsIgnoreCase(project.getType())) return;
        String resourceName = project.getOwnerName();
        try {
            var notes = json.readTree(project.getNotes() == null ? "{}" : project.getNotes());
            // selectedResource is the canonical employee for this assignment.
            // workingResource may be a different operational/substitute label.
            resourceName = notes.path("selectedResource").asText(notes.path("workingResource").asText(resourceName));
        } catch (Exception ignored) { }
        if (resourceName == null || resourceName.isBlank()) return;
        List<Long> resourceIds = jdbc.queryForList("SELECT id FROM business_records WHERE module='resources' AND type='resources' AND LOWER(party_name)=LOWER(?) ORDER BY id LIMIT 1", Long.class, resourceName);
        if (resourceIds.isEmpty()) return;
        Long resourceId = resourceIds.get(0);
        List<Long> allocationIds = jdbc.queryForList("SELECT id FROM project_resource_allocation WHERE project_id=? AND project_type='STAFFING' ORDER BY id DESC LIMIT 1", Long.class, project.getId());
        boolean closed = isClosed(project);
        if (allocationIds.isEmpty()) {
            jdbc.update("""
                    INSERT INTO project_resource_allocation(resource_id,project_id,project_type,role_designation,
                    allocation_start_date,actual_release_date,last_billing_date,allocation_status,allocation_percentage,
                    monthly_billing,billing_currency,billable,created_by,updated_by) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """, resourceId, project.getId(), "STAFFING", project.getCategory(), project.getRecordDate(),
                    closed ? project.getClosedDate() : null, closed ? project.getClosedDate() : null,
                    closed ? "CLOSED" : "ACTIVE", 100, project.getAmount(), currency(project), true,
                    project.getCreatedBy(), project.getUpdatedBy());
        } else {
            jdbc.update("""
                    UPDATE project_resource_allocation SET resource_id=?,role_designation=?,allocation_start_date=?,
                    actual_release_date=?,last_billing_date=?,allocation_status=?,monthly_billing=?,billing_currency=?,updated_by=? WHERE id=?
                    """, resourceId, project.getCategory(), project.getRecordDate(), closed ? project.getClosedDate() : null,
                    closed ? project.getClosedDate() : null, closed ? "CLOSED" : "ACTIVE", project.getAmount(),
                    currency(project), project.getUpdatedBy(), allocationIds.get(0));
        }
    }

    @Transactional
    public void recordFixedCostAssignment(Long projectId, Long resourceId, String role, LocalDate startDate,
                                          boolean active, LocalDate endDate, String reason) {
        List<Long> ids = jdbc.queryForList("""
                SELECT id FROM project_resource_allocation WHERE project_id=? AND resource_id=?
                AND project_type='FIXED_COST' AND allocation_start_date=? ORDER BY id DESC LIMIT 1
                """, Long.class, projectId, resourceId, startDate);
        if (ids.isEmpty()) {
            jdbc.update("""
                    INSERT INTO project_resource_allocation(resource_id,project_id,project_type,role_designation,
                    allocation_start_date,actual_release_date,allocation_status,allocation_percentage,billable,
                    release_confirmation_status,release_reason,created_by,updated_by) VALUES(?,?,?,?,?,?,?,?,FALSE,?,?,?,?)
                    """, resourceId, projectId, "FIXED_COST", role, startDate, endDate,
                    active ? "ACTIVE" : "RELEASED", 100, active ? null : "RELEASED", clean(reason),
                    currentUsers.getCurrentUserId(), currentUsers.getCurrentUserId());
        } else if (!active) {
            jdbc.update("""
                    UPDATE project_resource_allocation SET actual_release_date=?,allocation_status='RELEASED',
                    release_confirmation_status='RELEASED',release_reason=?,updated_by=? WHERE id=?
                    """, endDate, clean(reason), currentUsers.getCurrentUserId(), ids.get(0));
        }
    }

    public BigDecimal salaryAt(Long resourceId, LocalDate date) {
        List<BigDecimal> values = jdbc.query("SELECT revised_salary FROM resource_salary_history WHERE resource_id=? AND effective_date<=? ORDER BY effective_date DESC,id DESC LIMIT 1",
                (rs, row) -> rs.getBigDecimal(1), resourceId, date);
        return values.isEmpty() ? null : values.get(0);
    }

    private BusinessRecord resource(Long id, boolean lock) {
        return (lock ? records.findResourceForUpdate(id) : records.findByModuleAndTypeAndId("resources", "resources", id))
                .orElseThrow(() -> new IllegalArgumentException("Resource does not exist."));
    }
    private BusinessRecord project(Long id) { return records.findById(id).filter(x -> "projects".equalsIgnoreCase(x.getModule())).orElseThrow(() -> new IllegalArgumentException("Project does not exist.")); }
    private AllocationEntry allocation(Long projectId, Long id) { return allocationRows("WHERE a.project_id=? AND a.id=?", projectId, id).stream().findFirst().orElseThrow(() -> new IllegalArgumentException("Resource Allocation does not exist for this Project.")); }
    private List<AllocationEntry> allocationRows(String clause, Object... args) {
        return jdbc.query("""
                SELECT a.*,p.party_name project_name,p.type,p.party_city client,r.party_name resource_name,
                COALESCE(r.employment_status,'ACTIVE') employment_status,r.last_working_day FROM project_resource_allocation a
                JOIN business_records p ON p.id=a.project_id JOIN business_records r ON r.id=a.resource_id
                """ + clause, (rs, row) -> new AllocationEntry(rs.getLong("id"), rs.getLong("resource_id"), rs.getString("resource_name"),
                rs.getString("employment_status"), rs.getObject("last_working_day", LocalDate.class), rs.getLong("project_id"),
                rs.getString("project_name"), rs.getString("project_type"), rs.getString("client"), rs.getString("role_designation"),
                rs.getObject("allocation_start_date", LocalDate.class), rs.getObject("planned_release_date", LocalDate.class),
                rs.getObject("actual_release_date", LocalDate.class), rs.getObject("last_billing_date", LocalDate.class),
                rs.getString("allocation_status"), rs.getBigDecimal("allocation_percentage"), rs.getBigDecimal("monthly_billing"),
                rs.getString("billing_currency"), rs.getBoolean("billable"), rs.getString("release_confirmation_status"),
                rs.getString("release_reason"), rs.getString("remarks")), args);
    }
    private SalaryRevision salary(ResultSet rs) throws SQLException { return new SalaryRevision(rs.getLong("id"), rs.getLong("resource_id"), rs.getBigDecimal("previous_salary"), rs.getBigDecimal("revised_salary"), rs.getString("salary_currency"), rs.getObject("effective_date", LocalDate.class), rs.getBigDecimal("revision_amount"), rs.getBigDecimal("revision_percentage"), rs.getString("revision_reason"), rs.getString("remarks"), nullableLong(rs,"updated_by"), rs.getString("updated_by_name"), rs.getObject("updated_at", LocalDateTime.class)); }
    private Availability availability(ResultSet rs, LocalDate today) throws SQLException { String employment = rs.getString("employment_status"); LocalDate lastDay = rs.getObject("last_working_day", LocalDate.class); int active = rs.getInt("active_count"); BigDecimal percent = rs.getBigDecimal("allocated_percentage"); LocalDate release = rs.getObject("planned_release", LocalDate.class); String state = List.of("RELIEVED","INACTIVE").contains(employment) ? "UNAVAILABLE" : active == 0 ? "AVAILABLE" : release != null ? "RELEASING_SOON" : percent.compareTo(BigDecimal.valueOf(100)) < 0 ? "PARTIALLY_AVAILABLE" : "ALLOCATED"; return new Availability(rs.getLong("id"), rs.getString("party_name"), employment, lastDay, lastDay == null ? -1 : Math.max(0, ChronoUnit.DAYS.between(today,lastDay)), state, active, percent, release, release == null ? -1 : Math.max(0, ChronoUnit.DAYS.between(today,release))); }
    private BigDecimal salaryFromMaster(BusinessRecord resource) { try { return json.readTree(resource.getNotes()).path("monthlySalary").decimalValue(); } catch (Exception e) { return BigDecimal.ZERO; } }
    private String cleanCurrency(String value, BusinessRecord resource) { String result=value; if(result==null||result.isBlank()){try{result=json.readTree(resource.getNotes()).path("salaryCurrency").asText("INR");}catch(Exception ignored){result="INR";}} result=result.trim().toUpperCase(Locale.ROOT); if(!result.matches("[A-Z]{3}"))throw new IllegalArgumentException("Salary Currency must be a three-letter currency code."); return result; }
    private void updateMasterSalary(BusinessRecord resource, BigDecimal salary, String currency) { try { ObjectNode notes=(ObjectNode)json.readTree(resource.getNotes()==null?"{}":resource.getNotes()); notes.put("monthlySalary",salary); notes.put("salaryCurrency",currency); resource.setNotes(json.writeValueAsString(notes)); resource.setUpdatedBy(currentUsers.getCurrentUserId()); records.save(resource); } catch(Exception e){throw new IllegalArgumentException("Unable to update the Resource salary.");} }
    private LocalDate latestEffectiveDate(Long id, LocalDate date) { return jdbc.queryForObject("SELECT MAX(effective_date) FROM resource_salary_history WHERE resource_id=? AND effective_date<=?", LocalDate.class,id,date); }
    private String normalized(String value) { return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace(' ','_'); }
    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String currency(BusinessRecord project) { String value=project.getPaymentMode()==null?"INR":project.getPaymentMode().trim(); return value.substring(0,Math.min(3,value.length())).toUpperCase(Locale.ROOT); }
    private boolean isClosed(BusinessRecord project) { return "CLOSE".equalsIgnoreCase(project.getStatus()) || "CLOSED".equalsIgnoreCase(project.getStatus()); }
    private Long nullableLong(ResultSet rs,String column)throws SQLException { long value=rs.getLong(column); return rs.wasNull()?null:value; }
}
