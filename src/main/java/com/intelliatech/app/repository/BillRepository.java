package com.intelliatech.app.repository;

import com.intelliatech.app.entity.Bill;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface BillRepository extends JpaRepository<Bill, Long>, JpaSpecificationExecutor<Bill> {
    Optional<Bill> findByIdAndOrganizationIdAndDeletedFalse(Long id, Long organizationId);
    boolean existsByOrganizationIdAndBillNumberIgnoreCaseAndDeletedFalse(Long organizationId, String billNumber);
    boolean existsByOrganizationIdAndBillNumberIgnoreCaseAndDeletedFalseAndIdNot(Long organizationId, String billNumber, Long id);
    List<Bill> findAllByOrganizationIdAndDeletedFalse(Long organizationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Bill b where b.id = :id and b.organizationId = :organizationId and b.deleted = false")
    Optional<Bill> findForUpdate(@Param("id") Long id, @Param("organizationId") Long organizationId);
}

