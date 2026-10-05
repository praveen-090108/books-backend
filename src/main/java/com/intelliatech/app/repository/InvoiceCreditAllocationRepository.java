package com.intelliatech.app.repository;

import com.intelliatech.app.entity.InvoiceCreditAllocation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceCreditAllocationRepository extends JpaRepository<InvoiceCreditAllocation, Long> {
    List<InvoiceCreditAllocation> findAllByInvoiceIdAndReversedFalse(Long invoiceId);
    Optional<InvoiceCreditAllocation> findByInvoiceIdAndSourceTypeAndSourceReference(Long invoiceId, String sourceType, String sourceReference);
    long countByInvoiceIdAndReversedFalse(Long invoiceId);
}
