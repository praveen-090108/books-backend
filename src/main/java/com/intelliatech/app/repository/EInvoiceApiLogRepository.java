package com.intelliatech.app.repository;

import com.intelliatech.app.entity.EInvoiceApiLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EInvoiceApiLogRepository extends JpaRepository<EInvoiceApiLog, Long> {
    List<EInvoiceApiLog> findAllByInvoiceIdOrderByCreatedAtDesc(Long invoiceId);
    List<EInvoiceApiLog> findAllByCreditNoteIdOrderByCreatedAtDesc(Long creditNoteId);
}
