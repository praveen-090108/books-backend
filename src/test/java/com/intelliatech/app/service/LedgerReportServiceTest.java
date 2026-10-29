package com.intelliatech.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LedgerReportServiceTest {
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    @Test
    void calculatesHistoricalOpeningRunningAndClosingBalances() {
        var entries = List.of(
                entry("jan", "2026-01-10", 10, "Invoice", "50000.00", "0.00"),
                entry("feb", "2026-02-10", 20, "Payment", "0.00", "20000.00"),
                entry("mar", "2026-03-10", 10, "Invoice", "30000.00", "0.00"),
                entry("apr", "2026-04-10", 20, "Payment", "0.00", "10000.00"));

        var result = LedgerReportService.calculate(entries, LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30"), "ALL", true);

        assertThat(result.opening()).isEqualByComparingTo("60000.00");
        assertThat(result.debit()).isEqualByComparingTo("0.00");
        assertThat(result.credit()).isEqualByComparingTo("10000.00");
        assertThat(result.closing()).isEqualByComparingTo("50000.00");
        assertThat(result.opening().add(result.debit()).subtract(result.credit())).isEqualByComparingTo(result.closing());
        assertThat(result.lines()).singleElement().satisfies(line -> {
            assertThat(line.runningBalance()).isEqualByComparingTo("50000.00");
            assertThat(line.balanceSide()).isEqualTo("Dr");
        });
    }

    @Test
    void usesStableSequenceWhenTransactionsSharePostingDate() {
        LocalDate date = LocalDate.parse("2026-04-05");
        var entries = List.of(
                new LedgerReportService.Entry("payment", date, 20, "Payment", "Payment", "PAY-1", ZERO, new BigDecimal("100.00"), "POSTED", null),
                new LedgerReportService.Entry("invoice", date, 10, "Invoice", "Invoice", "INV-1", new BigDecimal("250.00"), ZERO, "POSTED", null));

        var result = LedgerReportService.calculate(entries, date, date, "ALL", true);

        assertThat(result.lines()).extracting(line -> line.referenceNumber()).containsExactly("INV-1", "PAY-1");
        assertThat(result.lines()).extracting(line -> line.runningBalance()).containsExactly(new BigDecimal("250.00"), new BigDecimal("150.00"));
    }

    @Test
    void representsVendorPayableAsCreditBalance() {
        LocalDate date = LocalDate.parse("2026-04-05");
        var entries = List.of(
                new LedgerReportService.Entry("bill", date, 10, "Bill", "Bill", "BILL-1", ZERO, new BigDecimal("1000.00"), "OPEN", null),
                new LedgerReportService.Entry("payment", date.plusDays(1), 20, "Payment Made", "Payment", "PAY-1", new BigDecimal("400.00"), ZERO, "POSTED", null));

        var result = LedgerReportService.calculate(entries, date, date.plusDays(1), "ALL", true);

        assertThat(result.closing()).isEqualByComparingTo("-600.00");
        assertThat(result.lines().get(0).balanceSide()).isEqualTo("Cr");
        assertThat(result.lines().get(1).balanceSide()).isEqualTo("Cr");
    }

    @Test
    void generatesPortraitLedgerPdf() throws Exception {
        LocalDate from = LocalDate.parse("2026-07-01");
        var lines = List.of(
                line("1", "2026-07-15", "Sales Invoice", "Invoice", "INT-2026-089", "159300.00", "0.00", "159300.00"),
                line("2", "2026-07-15", "Sales Invoice", "Invoice", "INT-2026-090", "212400.00", "0.00", "371700.00"),
                line("3", "2026-07-16", "Credit Note Applied", "Credit Note", "CN-88590", "0.00", "260190.00", "111510.00"));
        var response = new com.intelliatech.app.dto.response.LedgerStatementResponse(
                "CUSTOMER",
                new com.intelliatech.app.dto.response.LedgerStatementResponse.Party(1L,"CUS-44737","Neutrinos Software Services Pvt Ltd","accounts@example.com","+91 98765 43210","MG Road, Bengaluru - 560001"),
                new com.intelliatech.app.dto.response.LedgerStatementResponse.Organization("Intelliatech Solutions Pvt Ltd","Plot No. 120, Scheme No. 78, Indore, Madhya Pradesh - 452001","23AAECI4774Q1Z2","accounts@example.com","+91 7804814213",null),
                from,LocalDate.parse("2026-09-29"),"INR",ZERO,new BigDecimal("371700.00"),new BigDecimal("260190.00"),new BigDecimal("111510.00"),lines);

        byte[] pdf = new LedgerReportService(null,null,null,null).pdf(response);

        assertThat(pdf).startsWith(new byte[]{'%', 'P', 'D', 'F'});
        Files.write(Path.of("target/ledger-statement-preview.pdf"),pdf);
    }

    private LedgerReportService.Entry entry(String id, String date, long sequence, String type, String debit, String credit) {
        return new LedgerReportService.Entry(id, LocalDate.parse(date), sequence, type, type, id,
                new BigDecimal(debit), new BigDecimal(credit), "POSTED", null);
    }

    private com.intelliatech.app.dto.response.LedgerStatementResponse.Line line(String id,String date,String particulars,String type,String reference,String debit,String credit,String running){
        BigDecimal balance=new BigDecimal(running);
        return new com.intelliatech.app.dto.response.LedgerStatementResponse.Line(id,LocalDate.parse(date),particulars,type,reference,new BigDecimal(debit),new BigDecimal(credit),balance,balance.signum()<0?"Cr":"Dr","POSTED",null);
    }
}
