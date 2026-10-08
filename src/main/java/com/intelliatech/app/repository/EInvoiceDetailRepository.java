package com.intelliatech.app.repository;

import com.intelliatech.app.entity.EInvoiceDetail;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EInvoiceDetailRepository extends JpaRepository<EInvoiceDetail, Long> {
    Optional<EInvoiceDetail> findByInvoiceId(Long invoiceId);
    Optional<EInvoiceDetail> findByCreditNoteId(Long creditNoteId);
    Optional<EInvoiceDetail> findByIrn(String irn);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select detail from EInvoiceDetail detail join fetch detail.invoice where detail.invoice.id = :invoiceId")
    Optional<EInvoiceDetail> findByInvoiceIdForUpdate(@Param("invoiceId") Long invoiceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select detail from EInvoiceDetail detail join fetch detail.creditNote where detail.creditNote.id = :creditNoteId")
    Optional<EInvoiceDetail> findByCreditNoteIdForUpdate(@Param("creditNoteId") Long creditNoteId);
}
