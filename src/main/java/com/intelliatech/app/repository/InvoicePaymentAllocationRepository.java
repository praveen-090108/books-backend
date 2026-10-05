package com.intelliatech.app.repository;

import com.intelliatech.app.entity.InvoicePaymentAllocation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoicePaymentAllocationRepository extends JpaRepository<InvoicePaymentAllocation, Long> {

    List<InvoicePaymentAllocation> findAllByInvoiceId(Long invoiceId);

    List<InvoicePaymentAllocation> findAllByPaymentIdOrderById(Long paymentId);

    Optional<InvoicePaymentAllocation> findByPaymentIdAndInvoiceId(Long paymentId, Long invoiceId);

    @Query("select allocation from InvoicePaymentAllocation allocation join fetch allocation.payment where allocation.invoice.id = :invoiceId and allocation.payment.reversed = false")
    List<InvoicePaymentAllocation> findActiveByInvoiceId(@Param("invoiceId") Long invoiceId);

    @Query("select allocation from InvoicePaymentAllocation allocation join fetch allocation.payment where allocation.invoice.id = :invoiceId order by allocation.payment.createdAt desc")
    List<InvoicePaymentAllocation> findHistoryByInvoiceId(@Param("invoiceId") Long invoiceId);
}
