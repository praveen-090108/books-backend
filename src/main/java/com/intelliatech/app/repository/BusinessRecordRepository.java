package com.intelliatech.app.repository;

import com.intelliatech.app.entity.BusinessRecord;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import com.intelliatech.app.entity.Department;

public interface BusinessRecordRepository extends JpaRepository<BusinessRecord, Long>, JpaSpecificationExecutor<BusinessRecord> {

    /**
     * Fetch the reporting manager with paged business records. Resource list
     * responses expose the manager name, and allowing the mapper to resolve the
     * lazy association row-by-row caused an avoidable N+1 query sequence.
     */
    @Override
    @EntityGraph(attributePaths = "reportingManager")
    Page<BusinessRecord> findAll(Specification<BusinessRecord> specification, Pageable pageable);

    Optional<BusinessRecord> findByModuleAndTypeAndId(String module, String type, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from BusinessRecord r where r.module='sales' and r.type='invoices' and r.id=:id")
    Optional<BusinessRecord> findInvoiceForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from BusinessRecord r where r.module='sales' and r.type='creditNotes' and r.id=:id")
    Optional<BusinessRecord> findCreditNoteForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from BusinessRecord r where r.module='projects' and r.type='fixedCost' and r.id=:id")
    Optional<BusinessRecord> findFixedCostProjectForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from BusinessRecord r where r.module='projects' and r.type='staffing' and r.id=:id")
    Optional<BusinessRecord> findStaffingProjectForUpdate(@Param("id") Long id);

    Optional<BusinessRecord> findFirstByModuleAndTypeAndPartyNameIgnoreCase(
            String module,
            String type,
            String partyName
    );

    Optional<BusinessRecord> findFirstByModuleAndTypeOrderByRecordDateDesc(String module, String type);

    Optional<BusinessRecord> findByRecordNumber(String recordNumber);

    List<BusinessRecord> findAllByModuleAndTypeAndPartyNameIgnoreCaseOrderByRecordDateDesc(
            String module,
            String type,
            String partyName
    );

    boolean existsByRecordNumber(String recordNumber);

    @Query("select record.recordNumber from BusinessRecord record where record.module = :module and record.type = :type")
    List<String> findRecordNumbersByModuleAndType(@Param("module") String module, @Param("type") String type);

    List<BusinessRecord> findByRecordDateBetween(LocalDate start, LocalDate end);

    List<BusinessRecord> findAllByModuleAndTypeOrderByPartyNameAsc(String module, String type);

    @Query("select r from BusinessRecord r where r.module = 'resources' and r.type = 'resources' " +
            "and r.department = :department and upper(r.status) = 'ACTIVE' " +
            "and (:excludeId is null or r.id <> :excludeId) order by r.partyName")
    List<BusinessRecord> findEligibleReportingManagers(
            @Param("department") Department department,
            @Param("excludeId") Long excludeId
    );
}
