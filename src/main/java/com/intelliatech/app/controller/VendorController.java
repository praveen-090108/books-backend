package com.intelliatech.app.controller;

import com.intelliatech.app.dto.request.VendorRequest;
import com.intelliatech.app.dto.request.VendorStatementEmailRequest;
import com.intelliatech.app.dto.request.VendorStatusRequest;
import com.intelliatech.app.dto.response.VendorResponse;
import com.intelliatech.app.dto.response.VendorStatementResponse;
import com.intelliatech.app.dto.response.VendorSummaryResponse;
import com.intelliatech.app.dto.response.VendorTransactionResponse;
import com.intelliatech.app.service.VendorService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/vendors")
@RequiredArgsConstructor
public class VendorController {

    private final VendorService vendorService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PURCHASE_VENDORS_VIEW')")
    public Page<VendorResponse> findAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String payableStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @PageableDefault(size = 10, sort = "createdAt") Pageable pageable
    ) {
        return vendorService.findAll(search, status, payableStatus, dateFrom, dateTo, pageable);
    }

    @GetMapping("/summary")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PURCHASE_VENDORS_VIEW')")
    public VendorSummaryResponse summary() {
        return vendorService.summary();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PURCHASE_VENDORS_VIEW')")
    public VendorResponse findById(@PathVariable Long id) {
        return vendorService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PURCHASE_VENDORS_CREATE')")
    public VendorResponse create(@Valid @RequestBody VendorRequest request) {
        return vendorService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PURCHASE_VENDORS_EDIT')")
    public VendorResponse update(@PathVariable Long id, @Valid @RequestBody VendorRequest request) {
        return vendorService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PURCHASE_VENDORS_EDIT')")
    public VendorResponse updateStatus(@PathVariable Long id, @Valid @RequestBody VendorStatusRequest request) {
        return vendorService.updateStatus(id, request);
    }

    @PostMapping("/{id}/clone")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PURCHASE_VENDORS_CREATE')")
    public VendorResponse cloneVendor(@PathVariable Long id) {
        return vendorService.cloneVendor(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PURCHASE_VENDORS_DELETE')")
    public void delete(@PathVariable Long id) {
        vendorService.delete(id);
    }

    @GetMapping("/{id}/transactions")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PURCHASE_VENDORS_VIEW')")
    public Page<VendorTransactionResponse> transactions(
            @PathVariable Long id,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String transactionType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @PageableDefault(size = 10, sort = "recordDate") Pageable pageable
    ) {
        return vendorService.transactions(id, search, transactionType, status, dateFrom, dateTo, pageable);
    }

    @GetMapping("/{id}/statement")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PURCHASE_VENDORS_VIEW')")
    public VendorStatementResponse statement(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "all") String transactionType
    ) {
        return vendorService.statement(id, dateFrom, dateTo, transactionType);
    }

    @GetMapping("/{id}/statement/pdf")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PURCHASE_VENDORS_DOWNLOAD')")
    public ResponseEntity<byte[]> statementPdf(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "all") String transactionType
    ) {
        VendorResponse vendor = vendorService.findById(id);
        return download(
                vendorService.statementPdf(id, dateFrom, dateTo, transactionType),
                MediaType.APPLICATION_PDF,
                statementFilename(vendor.vendorName(), dateFrom, dateTo, "pdf")
        );
    }

    @GetMapping("/{id}/statement/excel")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PURCHASE_VENDORS_DOWNLOAD')")
    public ResponseEntity<byte[]> statementExcel(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "all") String transactionType
    ) {
        VendorResponse vendor = vendorService.findById(id);
        return download(
                vendorService.statementExcel(id, dateFrom, dateTo, transactionType),
                MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
                statementFilename(vendor.vendorName(), dateFrom, dateTo, "xlsx")
        );
    }

    @PostMapping("/{id}/statement/email")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PURCHASE_VENDORS_EMAIL')")
    public Map<String, String> emailStatement(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "all") String transactionType,
            @Valid @RequestBody VendorStatementEmailRequest request
    ) {
        return vendorService.emailStatement(id, dateFrom, dateTo, transactionType, request);
    }

    private ResponseEntity<byte[]> download(byte[] bytes, MediaType mediaType, String filename) {
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(bytes);
    }

    private String statementFilename(String vendorName, LocalDate dateFrom, LocalDate dateTo, String extension) {
        String safeVendor = vendorName.replaceAll("[^A-Za-z0-9]+", "-").replaceAll("(^-|-$)", "");
        String from = dateFrom == null ? "start" : dateFrom.toString();
        String to = dateTo == null ? LocalDate.now().toString() : dateTo.toString();
        return "Vendor-Statement-" + safeVendor + "-" + from + "-to-" + to + "." + extension;
    }
}
