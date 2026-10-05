package com.intelliatech.app.repository;

import com.intelliatech.app.entity.InvoiceLifecycle;
import com.intelliatech.app.entity.InvoiceStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoiceLifecycleRepository extends JpaRepository<InvoiceLifecycle, Long> {

    Optional<InvoiceLifecycle> findByInvoiceId(Long invoiceId);

    List<InvoiceLifecycle> findAllByInvoiceIdIn(Collection<Long> invoiceIds);

    List<InvoiceLifecycle> findAllByStatusIn(Collection<InvoiceStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select lifecycle from InvoiceLifecycle lifecycle join fetch lifecycle.invoice where lifecycle.invoice.id = :invoiceId")
    Optional<InvoiceLifecycle> findByInvoiceIdForUpdate(@Param("invoiceId") Long invoiceId);
}
