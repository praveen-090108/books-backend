package com.intelliatech.app.repository;

import com.intelliatech.app.entity.PurchaseOrder;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long>, JpaSpecificationExecutor<PurchaseOrder> {
    Optional<PurchaseOrder> findByIdAndOrganizationIdAndDeletedFalse(Long id, Long organizationId);
    boolean existsByOrganizationIdAndPurchaseOrderNumber(Long organizationId, String purchaseOrderNumber);
    List<PurchaseOrder> findAllByOrganizationIdAndDeletedFalse(Long organizationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select po from PurchaseOrder po where po.id = :id and po.organizationId = :organizationId and po.deleted = false")
    Optional<PurchaseOrder> findForUpdate(@Param("id") Long id, @Param("organizationId") Long organizationId);
}
