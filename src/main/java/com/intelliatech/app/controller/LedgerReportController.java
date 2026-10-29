package com.intelliatech.app.controller;

import com.intelliatech.app.dto.response.LedgerStatementResponse;
import com.intelliatech.app.service.LedgerReportService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reports/ledger")
@RequiredArgsConstructor
public class LedgerReportController {
    private final LedgerReportService service;

    @GetMapping("/parties")
    @PreAuthorize("(#partyType.equalsIgnoreCase('CUSTOMER') and hasAuthority('REPORTS_CUSTOMER_LEDGER_VIEW')) or (#partyType.equalsIgnoreCase('VENDOR') and hasAuthority('REPORTS_VENDOR_LEDGER_VIEW'))")
    public List<Map<String,Object>> parties(@RequestParam String partyType,@RequestParam(required=false)String search){return service.parties(partyType,search);}

    @GetMapping("/statement")
    @PreAuthorize("(#partyType.equalsIgnoreCase('CUSTOMER') and hasAuthority('REPORTS_CUSTOMER_LEDGER_VIEW')) or (#partyType.equalsIgnoreCase('VENDOR') and hasAuthority('REPORTS_VENDOR_LEDGER_VIEW'))")
    public LedgerStatementResponse statement(@RequestParam String partyType,@RequestParam long partyId,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate fromDate,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate toDate,@RequestParam(defaultValue="INR")String currency,@RequestParam(defaultValue="ALL")String transactionType,@RequestParam(defaultValue="true")boolean includeOpening){return service.statement(partyType,partyId,fromDate,toDate,currency,transactionType,includeOpening);}

    @GetMapping("/pdf")
    @PreAuthorize("(#partyType.equalsIgnoreCase('CUSTOMER') and hasAuthority('REPORTS_CUSTOMER_LEDGER_DOWNLOAD')) or (#partyType.equalsIgnoreCase('VENDOR') and hasAuthority('REPORTS_VENDOR_LEDGER_DOWNLOAD'))")
    public ResponseEntity<byte[]> pdf(@RequestParam String partyType,@RequestParam long partyId,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate fromDate,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate toDate,@RequestParam(defaultValue="INR")String currency,@RequestParam(defaultValue="ALL")String transactionType,@RequestParam(defaultValue="true")boolean includeOpening){var data=service.statement(partyType,partyId,fromDate,toDate,currency,transactionType,includeOpening);return file(service.pdf(data),"application/pdf",filename(data,"pdf"));}

    @GetMapping("/print")
    @PreAuthorize("(#partyType.equalsIgnoreCase('CUSTOMER') and hasAuthority('REPORTS_CUSTOMER_LEDGER_PRINT')) or (#partyType.equalsIgnoreCase('VENDOR') and hasAuthority('REPORTS_VENDOR_LEDGER_PRINT'))")
    public ResponseEntity<byte[]> print(@RequestParam String partyType,@RequestParam long partyId,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate fromDate,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate toDate,@RequestParam(defaultValue="INR")String currency,@RequestParam(defaultValue="ALL")String transactionType,@RequestParam(defaultValue="true")boolean includeOpening){var data=service.statement(partyType,partyId,fromDate,toDate,currency,transactionType,includeOpening);return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).header(HttpHeaders.CONTENT_DISPOSITION,"inline; filename=\""+filename(data,"pdf")+"\"").body(service.pdf(data));}

    @GetMapping("/excel")
    @PreAuthorize("(#partyType.equalsIgnoreCase('CUSTOMER') and hasAuthority('REPORTS_CUSTOMER_LEDGER_DOWNLOAD')) or (#partyType.equalsIgnoreCase('VENDOR') and hasAuthority('REPORTS_VENDOR_LEDGER_DOWNLOAD'))")
    public ResponseEntity<byte[]> excel(@RequestParam String partyType,@RequestParam long partyId,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate fromDate,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate toDate,@RequestParam(defaultValue="INR")String currency,@RequestParam(defaultValue="ALL")String transactionType,@RequestParam(defaultValue="true")boolean includeOpening){var data=service.statement(partyType,partyId,fromDate,toDate,currency,transactionType,includeOpening);return file(service.excel(data),"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",filename(data,"xlsx"));}

    private ResponseEntity<byte[]> file(byte[] b,String type,String name){return ResponseEntity.ok().contentType(MediaType.parseMediaType(type)).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\""+name+"\"").body(b);}
    private String filename(LedgerStatementResponse d,String ext){return (d.partyType()+"_Ledger_"+d.party().name()+"_"+d.fromDate()+"_to_"+d.toDate()).replaceAll("[^A-Za-z0-9._-]+","_")+"."+ext;}
}
