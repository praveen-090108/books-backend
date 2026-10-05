package com.intelliatech.app.repository;

import com.intelliatech.app.entity.InvoiceCreditNoteLink;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceCreditNoteLinkRepository extends JpaRepository<InvoiceCreditNoteLink, Long> {
    List<InvoiceCreditNoteLink> findAllByInvoiceId(Long invoiceId);
    List<InvoiceCreditNoteLink> findAllByCreditNoteId(Long creditNoteId);
    Optional<InvoiceCreditNoteLink> findByInvoiceIdAndCreditNoteId(Long invoiceId, Long creditNoteId);
    long countByInvoiceId(Long invoiceId);
}
