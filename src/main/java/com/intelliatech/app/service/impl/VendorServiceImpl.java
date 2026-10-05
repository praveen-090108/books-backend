package com.intelliatech.app.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intelliatech.app.dto.request.VendorBankDetailsRequest;
import com.intelliatech.app.dto.request.VendorRequest;
import com.intelliatech.app.dto.request.VendorStatementEmailRequest;
import com.intelliatech.app.dto.request.VendorStatusRequest;
import com.intelliatech.app.dto.response.VendorBankDetailsResponse;
import com.intelliatech.app.dto.response.VendorFinancialSummaryResponse;
import com.intelliatech.app.dto.response.VendorResponse;
import com.intelliatech.app.dto.response.VendorStatementResponse;
import com.intelliatech.app.dto.response.VendorSummaryResponse;
import com.intelliatech.app.dto.response.VendorTransactionResponse;
import com.intelliatech.app.entity.BusinessRecord;
import com.intelliatech.app.entity.Vendor;
import com.intelliatech.app.entity.VendorBankDetails;
import com.intelliatech.app.entity.VendorStatus;
import com.intelliatech.app.exception.DuplicateResourceException;
import com.intelliatech.app.exception.ResourceConflictException;
import com.intelliatech.app.exception.ResourceNotFoundException;
import com.intelliatech.app.repository.BusinessRecordRepository;
import com.intelliatech.app.repository.VendorRepository;
import com.intelliatech.app.service.VendorService;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.BaseFont;
import jakarta.persistence.criteria.Predicate;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class VendorServiceImpl implements VendorService {

    private static final Long ORGANIZATION_ID = 1L;
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final Set<String> NON_TRANSACTION_TYPES = Set.of("vendors", "items", "vendorStatementEmails");

    private final VendorRepository vendorRepository;
    private final BusinessRecordRepository businessRecordRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public Page<VendorResponse> findAll(
            String search,
            String status,
            String payableStatus,
            LocalDate dateFrom,
            LocalDate dateTo,
            Pageable pageable
    ) {
        List<Vendor> matching = vendorRepository.findAll(
                vendorSpecification(search, status, dateFrom, dateTo),
                pageable.getSort()
        );
        if (StringUtils.hasText(payableStatus)) {
            matching = matching.stream()
                    .filter(vendor -> matchesPayableFilter(financialSummary(vendor), payableStatus))
                    .toList();
        }
        int start = Math.min((int) pageable.getOffset(), matching.size());
        int end = Math.min(start + pageable.getPageSize(), matching.size());
        List<VendorResponse> content = matching.subList(start, end).stream().map(this::toResponse).toList();
        return new PageImpl<>(content, pageable, matching.size());
    }

    @Override
    @Transactional(readOnly = true)
    public VendorResponse findById(Long id) {
        return toResponse(getVendor(id));
    }

    @Override
    @Transactional
    public VendorResponse create(VendorRequest request) {
        if (vendorRepository.existsByOrganizationIdAndVendorNameIgnoreCase(ORGANIZATION_ID, clean(request.vendorName()))) {
            throw new DuplicateResourceException("A vendor with this name already exists.");
        }
        Vendor vendor = new Vendor();
        vendor.setOrganizationId(ORGANIZATION_ID);
        vendor.setVendorNumber(nextVendorNumber());
        copy(request, vendor, true);
        Vendor saved = vendorRepository.save(vendor);
        syncLegacyVendor(saved);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public VendorResponse update(Long id, VendorRequest request) {
        Vendor vendor = getVendor(id);
        if (vendorRepository.existsByOrganizationIdAndVendorNameIgnoreCaseAndIdNot(
                ORGANIZATION_ID,
                clean(request.vendorName()),
                id
        )) {
            throw new DuplicateResourceException("A vendor with this name already exists.");
        }
        copy(request, vendor, false);
        Vendor saved = vendorRepository.save(vendor);
        syncLegacyVendor(saved);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public VendorResponse updateStatus(Long id, VendorStatusRequest request) {
        Vendor vendor = getVendor(id);
        vendor.setStatus(request.status());
        syncLegacyVendor(vendor);
        return toResponse(vendor);
    }

    @Override
    @Transactional
    public VendorResponse cloneVendor(Long id) {
        Vendor source = getVendor(id);
        Vendor clone = new Vendor();
        clone.setOrganizationId(ORGANIZATION_ID);
        clone.setVendorNumber(nextVendorNumber());
        copyVendor(source, clone);
        clone.setVendorName(uniqueCloneName(source.getVendorName()));
        clone.setDisplayName(clone.getVendorName());
        clone.setStatus(VendorStatus.ACTIVE);
        if (source.getBankDetails() != null) {
            VendorBankDetails bank = new VendorBankDetails();
            copyBank(source.getBankDetails(), bank);
            clone.setBankDetails(bank);
        }
        Vendor saved = vendorRepository.save(clone);
        syncLegacyVendor(saved);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Vendor vendor = getVendor(id);
        if (hasLinkedTransactions(vendor)) {
            throw new ResourceConflictException(
                    "This vendor cannot be deleted because transactions are associated with it."
            );
        }
        businessRecordRepository.findByRecordNumber(vendor.getVendorNumber())
                .filter(record -> "purchases".equals(record.getModule()) && "vendors".equals(record.getType()))
                .ifPresent(businessRecordRepository::delete);
        vendorRepository.delete(vendor);
    }

    @Override
    @Transactional(readOnly = true)
    public VendorSummaryResponse summary() {
        List<Vendor> vendors = vendorRepository.findAll(vendorSpecification(null, null, null, null));
        BigDecimal payables = ZERO;
        BigDecimal overdue = ZERO;
        BigDecimal paidThisMonth = ZERO;
        BigDecimal purchasesThisMonth = ZERO;
        YearMonth currentMonth = YearMonth.now();
        for (Vendor vendor : vendors) {
            BigDecimal derivedPaidThisMonth = ZERO;
            BigDecimal explicitPaidThisMonth = ZERO;
            for (BusinessRecord record : relatedTransactions(vendor)) {
                if (isPayableTransaction(record)) {
                    payables = payables.add(money(record.getBalanceAmount()));
                    if (record.getDueDate() != null
                            && record.getDueDate().isBefore(LocalDate.now())
                            && money(record.getBalanceAmount()).signum() > 0) {
                        overdue = overdue.add(money(record.getBalanceAmount()));
                    }
                    if (YearMonth.from(record.getRecordDate()).equals(currentMonth)) {
                        purchasesThisMonth = purchasesThisMonth.add(money(record.getAmount()));
                        derivedPaidThisMonth = derivedPaidThisMonth.add(
                                money(record.getAmount()).subtract(money(record.getBalanceAmount())).max(ZERO));
                    }
                } else if (isPayment(record) && YearMonth.from(record.getRecordDate()).equals(currentMonth)) {
                    explicitPaidThisMonth = explicitPaidThisMonth.add(money(record.getAmount()));
                }
            }
            paidThisMonth = paidThisMonth.add(explicitPaidThisMonth.max(derivedPaidThisMonth));
        }
        return new VendorSummaryResponse(
                vendors.size(),
                vendors.stream().filter(v -> v.getStatus() == VendorStatus.ACTIVE).count(),
                vendors.stream().filter(v -> v.getStatus() == VendorStatus.INACTIVE).count(),
                money(payables),
                money(overdue),
                money(paidThisMonth),
                money(purchasesThisMonth)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Page<VendorTransactionResponse> transactions(
            Long id,
            String search,
            String transactionType,
            String status,
            LocalDate dateFrom,
            LocalDate dateTo,
            Pageable pageable
    ) {
        Vendor vendor = getVendor(id);
        List<BusinessRecord> filtered = relatedTransactions(vendor).stream()
                .filter(record -> !StringUtils.hasText(search)
                        || contains(record.getRecordNumber(), search)
                        || contains(record.getReferenceNumber(), search)
                        || contains(record.getNotes(), search))
                .filter(record -> !StringUtils.hasText(transactionType)
                        || normalizeType(record.getType()).equalsIgnoreCase(normalizeType(transactionType)))
                .filter(record -> !StringUtils.hasText(status) || record.getStatus().equalsIgnoreCase(status))
                .filter(record -> dateFrom == null || !record.getRecordDate().isBefore(dateFrom))
                .filter(record -> dateTo == null || !record.getRecordDate().isAfter(dateTo))
                .sorted(transactionComparator(pageable))
                .toList();
        int start = Math.min((int) pageable.getOffset(), filtered.size());
        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        return new PageImpl<>(
                filtered.subList(start, end).stream().map(this::toTransactionResponse).toList(),
                pageable,
                filtered.size()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public VendorStatementResponse statement(Long id, LocalDate dateFrom, LocalDate dateTo, String transactionType) {
        Vendor vendor = getVendor(id);
        LocalDate effectiveFrom = dateFrom == null ? LocalDate.now().withDayOfMonth(1) : dateFrom;
        LocalDate effectiveTo = dateTo == null ? LocalDate.now() : dateTo;
        if (effectiveTo.isBefore(effectiveFrom)) throw new IllegalArgumentException("To date must be on or after From date.");

        List<BusinessRecord> all = relatedTransactions(vendor).stream()
                .filter(record -> matchesStatementType(record, transactionType))
                .sorted(Comparator.comparing(BusinessRecord::getRecordDate).thenComparing(BusinessRecord::getId))
                .toList();

        BigDecimal opening = all.stream()
                .filter(record -> record.getRecordDate().isBefore(effectiveFrom))
                .map(this::signedStatementAmount)
                .reduce(ZERO, BigDecimal::add);
        BigDecimal running = money(opening);
        BigDecimal debit = ZERO;
        BigDecimal credit = ZERO;
        BigDecimal purchases = ZERO;
        BigDecimal payments = ZERO;
        BigDecimal vendorCredits = ZERO;
        List<VendorStatementResponse.StatementLine> lines = new ArrayList<>();

        for (BusinessRecord record : all) {
            if (record.getRecordDate().isBefore(effectiveFrom) || record.getRecordDate().isAfter(effectiveTo)) continue;
            BigDecimal lineDebit = isCreditTransaction(record) ? ZERO : money(record.getAmount());
            BigDecimal lineCredit = isCreditTransaction(record) ? money(record.getAmount()) : ZERO;
            running = money(running.add(lineDebit).subtract(lineCredit));
            debit = debit.add(lineDebit);
            credit = credit.add(lineCredit);
            if (isPayableTransaction(record)) purchases = purchases.add(lineDebit);
            if (isPayment(record)) payments = payments.add(lineCredit);
            if (isVendorCredit(record)) vendorCredits = vendorCredits.add(lineCredit);
            lines.add(new VendorStatementResponse.StatementLine(
                    record.getRecordDate(),
                    displayType(record.getType()),
                    record.getRecordNumber(),
                    value(record.getNotes()),
                    record.getReferenceNumber(),
                    record.getDueDate(),
                    lineDebit,
                    lineCredit,
                    running,
                    record.getStatus(),
                    transactionViewPath(record)
            ));
        }
        var summary = new VendorStatementResponse.StatementSummary(
                money(opening),
                money(purchases),
                money(debit),
                money(credit),
                money(payments),
                money(vendorCredits),
                money(running)
        );
        return new VendorStatementResponse(
                companyDetails(),
                toResponse(vendor),
                effectiveFrom,
                effectiveTo,
                summary,
                lines
        );
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] statementPdf(Long id, LocalDate dateFrom, LocalDate dateTo, String transactionType) {
        VendorStatementResponse data = statement(id, dateFrom, dateTo, transactionType);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 38, 38, 34, 38);
            PdfWriter.getInstance(document, output);
            document.open();
            Font companyNameFont = statementFont(13, true, new Color(45, 45, 45));
            Font titleFont = statementFont(17, true, Color.BLACK);
            Font headingFont = statementFont(10, true, new Color(50, 50, 50));
            Font bodyFont = statementFont(9, false, new Color(50, 50, 50));
            Font bodyBoldFont = statementFont(9, true, new Color(50, 50, 50));
            Font moneyFont = statementCurrencyFont(9, new Color(50, 50, 50));
            Font whiteHeaderFont = statementFont(9, true, Color.WHITE);
            Font vendorNameFont = statementFont(10, true, new Color(66, 133, 232));

            PdfPTable companyHeader = new PdfPTable(new float[]{1.05f, 1f});
            companyHeader.setWidthPercentage(100);
            PdfPCell logoCell = borderlessCell();
            Image logo = statementLogo(data.company().logoUrl());
            if (logo != null) {
                logo.scaleToFit(220, 82);
                logo.setAlignment(Image.LEFT);
                logoCell.addElement(logo);
            } else {
                Paragraph wordmark = new Paragraph("INTELLIATECH", statementFont(24, true, new Color(37, 37, 37)));
                wordmark.setSpacingBefore(22);
                logoCell.addElement(wordmark);
            }
            companyHeader.addCell(logoCell);
            Paragraph companyDetails = new Paragraph();
            companyDetails.setAlignment(Element.ALIGN_RIGHT);
            companyDetails.add(new Phrase(value(data.company().name()) + "\n", companyNameFont));
            companyDetails.add(new Phrase(companyAddress(data.company()), bodyFont));
            companyHeader.addCell(borderlessCell(companyDetails, 0));
            document.add(companyHeader);

            PdfPTable statementHeading = new PdfPTable(new float[]{1f, 0.95f});
            statementHeading.setWidthPercentage(100);
            statementHeading.setSpacingBefore(18);
            PdfPCell recipientCell = borderlessCell();
            recipientCell.addElement(new Paragraph("To", bodyBoldFont));
            recipientCell.addElement(new Paragraph(data.vendor().vendorName(), vendorNameFont));
            if (StringUtils.hasText(data.vendor().companyName())
                    && !data.vendor().companyName().equalsIgnoreCase(data.vendor().vendorName())) {
                recipientCell.addElement(new Paragraph(data.vendor().companyName(), bodyFont));
            }
            recipientCell.addElement(new Paragraph(vendorAddressLines(data.vendor()), bodyFont));
            statementHeading.addCell(recipientCell);

            PdfPCell summaryCell = borderlessCell();
            Paragraph statementTitle = new Paragraph("STATEMENT OF ACCOUNTS", titleFont);
            statementTitle.setAlignment(Element.ALIGN_RIGHT);
            PdfPCell titleCell = new PdfPCell(statementTitle);
            titleCell.setBorder(Rectangle.BOTTOM);
            titleCell.setPaddingBottom(4);
            PdfPTable titleTable = new PdfPTable(1);
            titleTable.setWidthPercentage(100);
            titleTable.addCell(titleCell);
            PdfPCell periodCell = new PdfPCell(new Phrase(
                    formatStatementDate(data.dateFrom()) + " To " + formatStatementDate(data.dateTo()),
                    bodyFont
            ));
            periodCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            periodCell.setBorder(Rectangle.BOTTOM);
            periodCell.setPadding(6);
            titleTable.addCell(periodCell);
            summaryCell.addElement(titleTable);

            PdfPTable accountSummary = new PdfPTable(new float[]{1.45f, 0.75f});
            accountSummary.setWidthPercentage(100);
            accountSummary.setSpacingBefore(16);
            PdfPCell accountHeading = new PdfPCell(new Phrase("Account Summary", headingFont));
            accountHeading.setColspan(2);
            accountHeading.setBorder(Rectangle.NO_BORDER);
            accountHeading.setBackgroundColor(new Color(232, 232, 232));
            accountHeading.setPadding(7);
            accountSummary.addCell(accountHeading);
            addAccountSummaryRow(accountSummary, "Opening Balance", data.summary().openingBalance(), bodyFont, false);
            addAccountSummaryRow(accountSummary, "Billed Amount", data.summary().totalDebit(), bodyFont, false);
            addAccountSummaryRow(accountSummary, "Amount Paid", data.summary().totalCredit(), bodyFont, false);
            addAccountSummaryRow(accountSummary, "Balance Due", data.summary().closingBalance(), bodyFont, true);
            summaryCell.addElement(accountSummary);
            statementHeading.addCell(summaryCell);
            document.add(statementHeading);

            PdfPTable table = new PdfPTable(new float[]{1.05f, 1.6f, 2.2f, 1.15f, 1.15f, 1.2f});
            table.setWidthPercentage(100);
            table.setSpacingBefore(34);
            table.setHeaderRows(1);
            table.setSplitLate(false);
            addStatementHeader(table, List.of("Date", "Transactions", "Details", "Amount", "Payments", "Balance"), whiteHeaderFont);
            if (data.transactions().isEmpty()) {
                addStatementRow(table, List.of(
                        formatStatementDate(data.dateFrom()),
                        "***Opening Balance***",
                        "",
                        currency(data.summary().openingBalance()),
                        "",
                        currency(data.summary().openingBalance())
                ), bodyFont, false);
            }
            for (var line : data.transactions()) {
                addStatementRow(table, List.of(
                        formatStatementDate(line.date()),
                        line.transactionType() + "\n" + line.transactionNumber(),
                        value(StringUtils.hasText(line.description()) ? line.description() : line.referenceNumber()),
                        line.debit().signum() == 0 ? "" : currency(line.debit()),
                        line.credit().signum() == 0 ? "" : currency(line.credit()),
                        currency(line.balance())
                ), bodyFont, false);
            }
            PdfPCell footerSpacer = new PdfPCell(new Phrase("", bodyFont));
            footerSpacer.setColspan(4);
            footerSpacer.setBorder(Rectangle.TOP);
            footerSpacer.setPaddingTop(9);
            table.addCell(footerSpacer);
            PdfPCell balanceLabel = new PdfPCell(new Phrase("Balance Due", bodyBoldFont));
            balanceLabel.setHorizontalAlignment(Element.ALIGN_RIGHT);
            balanceLabel.setBorder(Rectangle.TOP);
            balanceLabel.setPadding(9);
            table.addCell(balanceLabel);
            PdfPCell balanceValue = new PdfPCell(new Phrase(currency(data.summary().closingBalance()), moneyFont));
            balanceValue.setHorizontalAlignment(Element.ALIGN_RIGHT);
            balanceValue.setBorder(Rectangle.TOP);
            balanceValue.setPadding(9);
            table.addCell(balanceValue);
            document.add(table);
            document.close();
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to generate vendor statement PDF.");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] statementExcel(Long id, LocalDate dateFrom, LocalDate dateTo, String transactionType) {
        VendorStatementResponse data = statement(id, dateFrom, dateTo, transactionType);
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Vendor Statement");
            var titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 16);
            CellStyle titleStyle = workbook.createCellStyle();
            titleStyle.setFont(titleFont);
            CellStyle headerStyle = workbook.createCellStyle();
            var headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            DataFormat format = workbook.createDataFormat();
            CellStyle moneyStyle = workbook.createCellStyle();
            moneyStyle.setDataFormat(format.getFormat("₹#,##0.00;[Red]-₹#,##0.00"));

            int rowIndex = 0;
            Row titleRow = sheet.createRow(rowIndex++);
            titleRow.createCell(0).setCellValue("STATEMENT OF ACCOUNTS");
            titleRow.getCell(0).setCellStyle(titleStyle);
            sheet.createRow(rowIndex++).createCell(0).setCellValue("Vendor: " + data.vendor().vendorName());
            sheet.createRow(rowIndex++).createCell(0).setCellValue(
                    "Period: " + formatDate(data.dateFrom()) + " to " + formatDate(data.dateTo())
            );
            sheet.createRow(rowIndex++).createCell(0).setCellValue("Currency: " + data.vendor().currency());
            rowIndex++;

            Row summaryHeader = sheet.createRow(rowIndex++);
            List<String> summaryLabels = List.of("Opening Balance", "Total Purchases", "Total Debit", "Total Credit", "Total Payments", "Vendor Credits", "Closing Balance");
            for (int index = 0; index < summaryLabels.size(); index++) {
                summaryHeader.createCell(index).setCellValue(summaryLabels.get(index));
                summaryHeader.getCell(index).setCellStyle(headerStyle);
            }
            Row summaryValues = sheet.createRow(rowIndex++);
            List<BigDecimal> summaryNumbers = List.of(
                    data.summary().openingBalance(),
                    data.summary().totalPurchases(),
                    data.summary().totalDebit(),
                    data.summary().totalCredit(),
                    data.summary().totalPayments(),
                    data.summary().totalVendorCredits(),
                    data.summary().closingBalance()
            );
            for (int index = 0; index < summaryNumbers.size(); index++) {
                summaryValues.createCell(index).setCellValue(summaryNumbers.get(index).doubleValue());
                summaryValues.getCell(index).setCellStyle(moneyStyle);
            }
            rowIndex += 2;
            int transactionHeaderIndex = rowIndex;
            Row transactionHeader = sheet.createRow(rowIndex++);
            List<String> columns = List.of("Date", "Transaction Type", "Transaction Number", "Description", "Reference", "Due Date", "Debit", "Credit", "Running Balance", "Status");
            for (int index = 0; index < columns.size(); index++) {
                transactionHeader.createCell(index).setCellValue(columns.get(index));
                transactionHeader.getCell(index).setCellStyle(headerStyle);
            }
            for (var line : data.transactions()) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(formatDate(line.date()));
                row.createCell(1).setCellValue(line.transactionType());
                row.createCell(2).setCellValue(line.transactionNumber());
                row.createCell(3).setCellValue(value(line.description()));
                row.createCell(4).setCellValue(value(line.referenceNumber()));
                row.createCell(5).setCellValue(formatDate(line.dueDate()));
                List<BigDecimal> amounts = List.of(line.debit(), line.credit(), line.balance());
                for (int index = 0; index < amounts.size(); index++) {
                    row.createCell(6 + index).setCellValue(amounts.get(index).doubleValue());
                    row.getCell(6 + index).setCellStyle(moneyStyle);
                }
                row.createCell(9).setCellValue(line.status());
            }
            sheet.createFreezePane(0, transactionHeaderIndex + 1);
            for (int index = 0; index < columns.size(); index++) sheet.autoSizeColumn(index);
            workbook.write(output);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to generate vendor statement Excel file.");
        }
    }

    @Override
    @Transactional
    public Map<String, String> emailStatement(
            Long id,
            LocalDate dateFrom,
            LocalDate dateTo,
            String transactionType,
            VendorStatementEmailRequest request
    ) {
        Vendor vendor = getVendor(id);
        byte[] attachment = statementPdf(id, dateFrom, dateTo, transactionType);
        BusinessRecord queued = new BusinessRecord();
        queued.setModule("communications");
        queued.setType("vendorStatementEmails");
        queued.setRecordNumber("VSE-" + System.currentTimeMillis());
        queued.setPartyName(vendor.getVendorName());
        queued.setPartyEmail(request.to());
        queued.setStatus("Queued");
        queued.setAmount(ZERO);
        queued.setBalanceAmount(ZERO);
        queued.setRecordDate(LocalDate.now());
        queued.setReferenceNumber(vendor.getVendorNumber());
        queued.setOwnerName("System");
        queued.setNotes(emailAuditNotes(request, dateFrom, dateTo, transactionType, attachment.length));
        businessRecordRepository.save(queued);
        return Map.of(
                "status", "queued",
                "message", "Vendor statement email was queued successfully.",
                "reference", queued.getRecordNumber()
        );
    }

    private void copy(VendorRequest request, Vendor vendor, boolean creating) {
        vendor.setVendorName(clean(request.vendorName()));
        vendor.setDisplayName(clean(request.displayName()));
        vendor.setCompanyName(clean(request.companyName()));
        vendor.setVendorType(clean(request.vendorType()));
        vendor.setSourceOfSupply(clean(request.sourceOfSupply()));
        vendor.setCurrency(clean(request.currency()));
        vendor.setPaymentTerms(clean(request.paymentTerms()));
        vendor.setTaxTreatment(clean(request.taxTreatment()));
        vendor.setGstin(upper(request.gstin()));
        vendor.setPan(upper(request.pan()));
        vendor.setStatus(request.status());
        vendor.setPrimaryContact(clean(request.primaryContact()));
        vendor.setEmail(lower(request.email()));
        vendor.setPhone(clean(request.phone()));
        vendor.setMobile(clean(request.mobile()));
        vendor.setWebsite(clean(request.website()));
        vendor.setBillingAddressLine1(clean(request.billingAddressLine1()));
        vendor.setBillingAddressLine2(clean(request.billingAddressLine2()));
        vendor.setBillingCity(clean(request.billingCity()));
        vendor.setBillingState(clean(request.billingState()));
        vendor.setBillingPincode(clean(request.billingPincode()));
        vendor.setBillingCountry(clean(request.billingCountry()));
        vendor.setShippingAddressLine1(clean(request.shippingAddressLine1()));
        vendor.setShippingAddressLine2(clean(request.shippingAddressLine2()));
        vendor.setShippingCity(clean(request.shippingCity()));
        vendor.setShippingState(clean(request.shippingState()));
        vendor.setShippingPincode(clean(request.shippingPincode()));
        vendor.setShippingCountry(clean(request.shippingCountry()));
        applyBankDetails(vendor, request.bankDetails(), creating);
    }

    private void applyBankDetails(Vendor vendor, VendorBankDetailsRequest request, boolean creating) {
        if (request == null || bankRequestEmpty(request)) {
            vendor.setBankDetails(null);
            return;
        }
        boolean accountEntered = StringUtils.hasText(request.accountNumber());
        String existingAccount = vendor.getBankDetails() == null ? null : vendor.getBankDetails().getAccountNumber();
        if (!accountEntered && creating) throw new IllegalArgumentException("Account number is required when bank details are entered.");
        if (!accountEntered && !StringUtils.hasText(existingAccount)) {
            throw new IllegalArgumentException("Account number is required when bank details are entered.");
        }
        if (accountEntered && !Objects.equals(clean(request.accountNumber()), clean(request.confirmAccountNumber()))) {
            throw new IllegalArgumentException("Account Number and Confirm Account Number must match.");
        }
        if (!StringUtils.hasText(request.accountHolderName())) {
            throw new IllegalArgumentException("Account Holder Name is required when bank details are entered.");
        }
        if (!StringUtils.hasText(request.bankName())) {
            throw new IllegalArgumentException("Bank Name is required when bank details are entered.");
        }
        VendorBankDetails bank = vendor.getBankDetails() == null ? new VendorBankDetails() : vendor.getBankDetails();
        bank.setAccountHolderName(clean(request.accountHolderName()));
        bank.setBeneficiaryName(clean(request.beneficiaryName()));
        bank.setBankName(clean(request.bankName()));
        if (accountEntered) bank.setAccountNumber(clean(request.accountNumber()));
        bank.setIfscCode(upper(request.ifscCode()));
        bank.setBranchName(clean(request.branchName()));
        bank.setAccountType(clean(request.accountType()));
        bank.setSwiftCode(upper(request.swiftCode()));
        bank.setIban(upper(request.iban()));
        bank.setBankCountry(clean(request.bankCountry()));
        bank.setBankAddress(clean(request.bankAddress()));
        bank.setUpiId(clean(request.upiId()));
        bank.setNotes(clean(request.notes()));
        vendor.setBankDetails(bank);
    }

    private boolean bankRequestEmpty(VendorBankDetailsRequest request) {
        return Stream.of(
                        request.accountHolderName(), request.beneficiaryName(), request.bankName(),
                        request.accountNumber(), request.confirmAccountNumber(), request.ifscCode(),
                        request.branchName(), request.accountType(), request.swiftCode(), request.iban(),
                        request.bankAddress(), request.upiId(), request.notes()
                )
                .noneMatch(StringUtils::hasText);
    }

    private VendorResponse toResponse(Vendor vendor) {
        return new VendorResponse(
                vendor.getId(),
                vendor.getVendorNumber(),
                vendor.getVendorName(),
                vendor.getDisplayName(),
                vendor.getCompanyName(),
                vendor.getVendorType(),
                vendor.getSourceOfSupply(),
                vendor.getCurrency(),
                vendor.getPaymentTerms(),
                vendor.getTaxTreatment(),
                vendor.getGstin(),
                vendor.getPan(),
                vendor.getStatus(),
                vendor.getPrimaryContact(),
                vendor.getEmail(),
                vendor.getPhone(),
                vendor.getMobile(),
                vendor.getWebsite(),
                vendor.getBillingAddressLine1(),
                vendor.getBillingAddressLine2(),
                vendor.getBillingCity(),
                vendor.getBillingState(),
                vendor.getBillingPincode(),
                vendor.getBillingCountry(),
                vendor.getShippingAddressLine1(),
                vendor.getShippingAddressLine2(),
                vendor.getShippingCity(),
                vendor.getShippingState(),
                vendor.getShippingPincode(),
                vendor.getShippingCountry(),
                toBankResponse(vendor.getBankDetails()),
                financialSummary(vendor),
                vendor.getCreatedAt(),
                vendor.getUpdatedAt()
        );
    }

    private VendorBankDetailsResponse toBankResponse(VendorBankDetails bank) {
        if (bank == null) return null;
        return new VendorBankDetailsResponse(
                bank.getAccountHolderName(),
                bank.getBeneficiaryName(),
                bank.getBankName(),
                mask(bank.getAccountNumber()),
                bank.getIfscCode(),
                bank.getBranchName(),
                bank.getAccountType(),
                bank.getSwiftCode(),
                bank.getIban(),
                bank.getBankCountry(),
                bank.getBankAddress(),
                bank.getUpiId(),
                bank.getNotes()
        );
    }

    private VendorFinancialSummaryResponse financialSummary(Vendor vendor) {
        BigDecimal purchases = ZERO;
        BigDecimal derivedPaid = ZERO;
        BigDecimal explicitPaid = ZERO;
        BigDecimal outstanding = ZERO;
        BigDecimal overdue = ZERO;
        BigDecimal credits = ZERO;
        for (BusinessRecord record : relatedTransactions(vendor)) {
            if (isPayableTransaction(record)) {
                purchases = purchases.add(money(record.getAmount()));
                outstanding = outstanding.add(money(record.getBalanceAmount()));
                derivedPaid = derivedPaid.add(
                        money(record.getAmount()).subtract(money(record.getBalanceAmount())).max(ZERO));
                if (record.getDueDate() != null && record.getDueDate().isBefore(LocalDate.now())
                        && money(record.getBalanceAmount()).signum() > 0) {
                    overdue = overdue.add(money(record.getBalanceAmount()));
                }
            } else if (isPayment(record)) {
                explicitPaid = explicitPaid.add(money(record.getAmount()));
            } else if (isVendorCredit(record)) {
                credits = credits.add(money(record.getBalanceAmount()));
            }
        }
        return new VendorFinancialSummaryResponse(
                money(purchases),
                money(explicitPaid.max(derivedPaid)),
                money(outstanding),
                money(overdue),
                money(credits)
        );
    }

    private List<BusinessRecord> relatedTransactions(Vendor vendor) {
        return businessRecordRepository.findAll(relatedTransactionSpecification(vendor));
    }

    private Specification<BusinessRecord> relatedTransactionSpecification(Vendor vendor) {
        return (root, query, builder) -> {
            List<Predicate> names = new ArrayList<>();
            List<String> vendorNames = Stream.of(
                            vendor.getVendorName(),
                            vendor.getDisplayName(),
                            vendor.getCompanyName()
                    )
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .distinct()
                    .toList();
            for (String name : vendorNames) {
                if (StringUtils.hasText(name)) names.add(builder.equal(builder.lower(root.get("partyName")), name.toLowerCase(Locale.ROOT)));
            }
            Predicate namePredicate = names.isEmpty()
                    ? builder.disjunction()
                    : builder.or(names.toArray(Predicate[]::new));
            return builder.and(
                    builder.equal(root.get("module"), "purchases"),
                    root.get("type").in(NON_TRANSACTION_TYPES).not(),
                    namePredicate
            );
        };
    }

    private Specification<Vendor> vendorSpecification(String search, String status, LocalDate dateFrom, LocalDate dateTo) {
        return (root, query, builder) -> {
            Predicate predicate = builder.equal(root.get("organizationId"), ORGANIZATION_ID);
            if (StringUtils.hasText(search)) {
                String like = "%" + search.toLowerCase(Locale.ROOT).trim() + "%";
                predicate = builder.and(predicate, builder.or(
                        builder.like(builder.lower(root.get("vendorName")), like),
                        builder.like(builder.lower(root.get("displayName")), like),
                        builder.like(builder.lower(root.get("companyName")), like),
                        builder.like(builder.lower(root.get("email")), like),
                        builder.like(builder.lower(root.get("phone")), like),
                        builder.like(builder.lower(root.get("gstin")), like)
                ));
            }
            if (StringUtils.hasText(status)) {
                predicate = builder.and(predicate, builder.equal(
                        root.get("status"),
                        VendorStatus.valueOf(status.toUpperCase(Locale.ROOT))
                ));
            }
            if (dateFrom != null) predicate = builder.and(predicate, builder.greaterThanOrEqualTo(root.get("createdAt"), dateFrom.atStartOfDay()));
            if (dateTo != null) predicate = builder.and(predicate, builder.lessThan(root.get("createdAt"), dateTo.plusDays(1).atStartOfDay()));
            return predicate;
        };
    }

    private void syncLegacyVendor(Vendor vendor) {
        BusinessRecord legacy = businessRecordRepository.findByRecordNumber(vendor.getVendorNumber()).orElseGet(BusinessRecord::new);
        legacy.setModule("purchases");
        legacy.setType("vendors");
        legacy.setRecordNumber(vendor.getVendorNumber());
        legacy.setPartyName(vendor.getVendorName());
        legacy.setPartyEmail(vendor.getEmail());
        legacy.setPartyPhone(vendor.getPhone());
        legacy.setPartyCity(Stream.of(vendor.getBillingCity(), vendor.getBillingState(), vendor.getBillingCountry())
                .filter(StringUtils::hasText).reduce((left, right) -> left + ", " + right).orElse(""));
        legacy.setCategory(vendor.getVendorType());
        legacy.setStatus(vendor.getStatus() == VendorStatus.ACTIVE ? "Active" : "Inactive");
        legacy.setAmount(ZERO);
        legacy.setBalanceAmount(ZERO);
        legacy.setRecordDate(LocalDate.now());
        legacy.setOwnerName(vendor.getPrimaryContact());
        legacy.setNotes("{\"source\":\"vendor-master\"}");
        businessRecordRepository.save(legacy);
    }

    private Vendor getVendor(Long id) {
        return vendorRepository.findByIdAndOrganizationId(id, ORGANIZATION_ID)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor not found."));
    }

    private String nextVendorNumber() {
        String latest = vendorRepository.findLatestVendorNumber(ORGANIZATION_ID);
        int next = 1;
        if (StringUtils.hasText(latest)) {
            try {
                next = Integer.parseInt(latest.replaceAll("\\D+", "")) + 1;
            } catch (NumberFormatException ignored) {
                next = (int) vendorRepository.countByOrganizationId(ORGANIZATION_ID) + 1;
            }
        }
        String candidate;
        do {
            candidate = "VEN-" + String.format("%04d", next++);
        } while (vendorRepository.existsByOrganizationIdAndVendorNumber(ORGANIZATION_ID, candidate));
        return candidate;
    }

    private boolean hasLinkedTransactions(Vendor vendor) {
        return !relatedTransactions(vendor).isEmpty();
    }

    private boolean matchesPayableFilter(VendorFinancialSummaryResponse summary, String filter) {
        return switch (filter.toLowerCase(Locale.ROOT)) {
            case "outstanding" -> summary.outstandingPayables().signum() > 0;
            case "overdue" -> summary.overdueAmount().signum() > 0;
            case "paid" -> summary.totalPaid().signum() > 0;
            case "purchases" -> summary.totalPurchases().signum() > 0;
            default -> true;
        };
    }

    private boolean isPayableTransaction(BusinessRecord record) {
        return Set.of("bills", "orders", "expenses").contains(record.getType());
    }

    private boolean isPayment(BusinessRecord record) {
        return Set.of("payments", "paymentsMade").contains(record.getType());
    }

    private boolean isVendorCredit(BusinessRecord record) {
        return Set.of("vendorCredits", "credits").contains(record.getType());
    }

    private boolean isCreditTransaction(BusinessRecord record) {
        return isPayment(record) || isVendorCredit(record);
    }

    private BigDecimal signedStatementAmount(BusinessRecord record) {
        return isCreditTransaction(record) ? money(record.getAmount()).negate() : money(record.getAmount());
    }

    private boolean matchesStatementType(BusinessRecord record, String transactionType) {
        return !StringUtils.hasText(transactionType)
                || "all".equalsIgnoreCase(transactionType)
                || normalizeType(record.getType()).equalsIgnoreCase(normalizeType(transactionType));
    }

    private String normalizeType(String type) {
        if (!StringUtils.hasText(type)) return "";
        return type.replaceAll("[\\s_-]", "").toLowerCase(Locale.ROOT);
    }

    private String displayType(String type) {
        return switch (type) {
            case "orders" -> "Purchase Order";
            case "bills" -> "Bill";
            case "expenses" -> "Expense";
            case "payments", "paymentsMade" -> "Payment Made";
            case "vendorCredits", "credits" -> "Vendor Credit";
            default -> type;
        };
    }

    private VendorTransactionResponse toTransactionResponse(BusinessRecord record) {
        return new VendorTransactionResponse(
                record.getId(),
                displayType(record.getType()),
                record.getRecordNumber(),
                record.getReferenceNumber(),
                record.getRecordDate(),
                record.getDueDate(),
                money(record.getAmount()),
                money(record.getAmount()).subtract(money(record.getBalanceAmount())).max(ZERO),
                money(record.getBalanceAmount()),
                record.getStatus(),
                transactionViewPath(record)
        );
    }

    private String transactionViewPath(BusinessRecord record) {
        return switch (record.getType()) {
            case "orders" -> "/purchases/orders/" + record.getId() + "/edit";
            case "bills" -> "/purchases/bills/" + record.getId() + "/edit";
            case "expenses" -> "/purchases/expenses/" + record.getId() + "/edit";
            default -> "";
        };
    }

    private Comparator<BusinessRecord> transactionComparator(Pageable pageable) {
        Comparator<BusinessRecord> comparator = Comparator.comparing(BusinessRecord::getRecordDate);
        if (pageable.getSort().stream().anyMatch(order -> order.getDirection().isDescending())) comparator = comparator.reversed();
        return comparator.thenComparing(BusinessRecord::getId);
    }

    private VendorStatementResponse.CompanyDetails companyDetails() {
        BusinessRecord organization = businessRecordRepository
                .findFirstByModuleAndTypeOrderByRecordDateDesc("settings", "organization")
                .orElse(null);
        BusinessRecord branding = businessRecordRepository
                .findFirstByModuleAndTypeOrderByRecordDateDesc("settings", "branding")
                .orElse(null);
        JsonNode notes = json(organization == null ? null : organization.getNotes());
        JsonNode brandNotes = json(branding == null ? null : branding.getNotes());
        return new VendorStatementResponse.CompanyDetails(
                organization == null ? "Company" : organization.getPartyName(),
                notes.path("address").asText(organization == null ? "" : value(organization.getPartyCity())),
                notes.path("state").asText(""),
                notes.path("pincode").asText(""),
                notes.path("country").asText(""),
                organization == null ? "" : value(organization.getReferenceNumber()),
                organization == null ? "" : value(organization.getPartyPhone()),
                organization == null ? "" : value(organization.getPartyEmail()),
                notes.path("website").asText(""),
                brandNotes.path("logoUrl").asText("")
        );
    }

    private JsonNode json(String value) {
        try {
            return StringUtils.hasText(value) ? objectMapper.readTree(value) : objectMapper.createObjectNode();
        } catch (Exception ignored) {
            return objectMapper.createObjectNode();
        }
    }

    private String emailAuditNotes(VendorStatementEmailRequest request, LocalDate from, LocalDate to, String type, int attachmentSize) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("to", request.to());
        data.put("cc", request.cc());
        data.put("bcc", request.bcc());
        data.put("subject", request.subject());
        data.put("dateFrom", from);
        data.put("dateTo", to);
        data.put("transactionType", type);
        data.put("attachmentBytes", attachmentSize);
        data.put("attachStatementPdf", request.attachStatementPdf());
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception ignored) {
            return "{\"status\":\"queued\"}";
        }
    }

    private void addPdfHeader(PdfPTable table, List<String> values, Font font) {
        for (String value : values) {
            PdfPCell cell = new PdfPCell(new Phrase(value, font));
            cell.setBackgroundColor(new Color(248, 250, 252));
            cell.setPadding(7);
            table.addCell(cell);
        }
    }

    private void addPdfRow(PdfPTable table, List<String> values, Font font) {
        for (String value : values) {
            PdfPCell cell = new PdfPCell(new Phrase(value, font));
            cell.setPadding(6);
            table.addCell(cell);
        }
    }

    private PdfPCell borderlessCell() {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(0);
        return cell;
    }

    private PdfPCell borderlessCell(Paragraph paragraph, float padding) {
        PdfPCell cell = new PdfPCell(paragraph);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(padding);
        return cell;
    }

    private Image statementLogo(String logoUrl) {
        if (!StringUtils.hasText(logoUrl)) return null;
        try {
            if (logoUrl.startsWith("data:image") && logoUrl.contains(",")) {
                return Image.getInstance(Base64.getDecoder().decode(logoUrl.substring(logoUrl.indexOf(',') + 1)));
            }
            return Image.getInstance(logoUrl);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void addAccountSummaryRow(
            PdfPTable table,
            String label,
            BigDecimal amount,
            Font font,
            boolean total
    ) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, font));
        labelCell.setBorder(total ? Rectangle.TOP : Rectangle.NO_BORDER);
        labelCell.setPadding(5);
        table.addCell(labelCell);
        PdfPCell amountCell = new PdfPCell(new Phrase(
                currency(amount),
                statementCurrencyFont(font.getSize(), font.getColor())
        ));
        amountCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        amountCell.setBorder(total ? Rectangle.TOP : Rectangle.NO_BORDER);
        amountCell.setPadding(5);
        table.addCell(amountCell);
    }

    private void addStatementHeader(PdfPTable table, List<String> values, Font font) {
        for (int index = 0; index < values.size(); index++) {
            PdfPCell cell = new PdfPCell(new Phrase(values.get(index), font));
            cell.setBorder(Rectangle.NO_BORDER);
            cell.setBackgroundColor(new Color(56, 58, 54));
            cell.setPadding(8);
            if (index >= 3) cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            table.addCell(cell);
        }
    }

    private void addStatementRow(PdfPTable table, List<String> values, Font font, boolean bold) {
        Font rowFont = bold ? statementFont(9, true, new Color(50, 50, 50)) : font;
        for (int index = 0; index < values.size(); index++) {
            Font cellFont = index >= 3
                    ? statementCurrencyFont(rowFont.getSize(), rowFont.getColor())
                    : rowFont;
            PdfPCell cell = new PdfPCell(new Phrase(values.get(index), cellFont));
            cell.setBorder(Rectangle.BOTTOM);
            cell.setBorderColor(new Color(220, 220, 220));
            cell.setPadding(7);
            if (index >= 3) cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            table.addCell(cell);
        }
    }

    private Font statementFont(float size, boolean bold, Color color) {
        List<String> candidates = bold
                ? List.of(
                        "/System/Library/Fonts/Supplemental/Arial Bold.ttf",
                        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
                )
                : List.of(
                        "/System/Library/Fonts/Supplemental/Arial.ttf",
                        "/System/Library/Fonts/Supplemental/Arial Unicode.ttf",
                        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
                );
        for (String candidate : candidates) {
            try {
                if (Files.isRegularFile(Path.of(candidate))) {
                    BaseFont baseFont = BaseFont.createFont(candidate, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                    return new Font(baseFont, size, Font.NORMAL, color);
                }
            } catch (Exception ignored) {
                // Try the next platform font before falling back to a built-in PDF font.
            }
        }
        return FontFactory.getFont(bold ? FontFactory.HELVETICA_BOLD : FontFactory.HELVETICA, size, color);
    }

    private Font statementCurrencyFont(float size, Color color) {
        List<String> candidates = List.of(
                "/System/Library/Fonts/Supplemental/Devanagari Sangam MN.ttc,0",
                "/System/Library/Fonts/Kohinoor.ttc,0",
                "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
        );
        for (String candidate : candidates) {
            try {
                String fontFile = candidate.contains(".ttc,")
                        ? candidate.substring(0, candidate.lastIndexOf(','))
                        : candidate;
                if (Files.isRegularFile(Path.of(fontFile))) {
                    BaseFont baseFont = BaseFont.createFont(candidate, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                    return new Font(baseFont, size, Font.NORMAL, color);
                }
            } catch (Exception ignored) {
                // Try the next currency-capable font before using the statement font.
            }
        }
        return statementFont(size, false, color);
    }

    private String companyAddress(VendorStatementResponse.CompanyDetails company) {
        return Stream.of(
                        company.address(),
                        Stream.of(company.state(), company.pincode()).filter(StringUtils::hasText).reduce((left, right) -> left + " " + right).orElse(""),
                        company.country(),
                        StringUtils.hasText(company.gstin()) ? "GSTIN " + company.gstin() : "",
                        company.phone(),
                        company.email(),
                        company.website()
                )
                .filter(StringUtils::hasText)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private String vendorAddressLines(VendorResponse vendor) {
        return Stream.of(
                        vendor.billingAddressLine1(),
                        vendor.billingAddressLine2(),
                        vendor.billingCity(),
                        Stream.of(vendor.billingPincode(), vendor.billingState()).filter(StringUtils::hasText).reduce((left, right) -> left + " " + right).orElse(""),
                        vendor.billingCountry(),
                        StringUtils.hasText(vendor.gstin()) ? "GSTIN " + vendor.gstin() : ""
                )
                .filter(StringUtils::hasText)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private String formatStatementDate(LocalDate date) {
        return date == null ? "" : date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    private void copyVendor(Vendor source, Vendor target) {
        target.setDisplayName(source.getDisplayName());
        target.setCompanyName(source.getCompanyName());
        target.setVendorType(source.getVendorType());
        target.setSourceOfSupply(source.getSourceOfSupply());
        target.setCurrency(source.getCurrency());
        target.setPaymentTerms(source.getPaymentTerms());
        target.setTaxTreatment(source.getTaxTreatment());
        target.setGstin(source.getGstin());
        target.setPan(source.getPan());
        target.setPrimaryContact(source.getPrimaryContact());
        target.setEmail(source.getEmail());
        target.setPhone(source.getPhone());
        target.setMobile(source.getMobile());
        target.setWebsite(source.getWebsite());
        target.setBillingAddressLine1(source.getBillingAddressLine1());
        target.setBillingAddressLine2(source.getBillingAddressLine2());
        target.setBillingCity(source.getBillingCity());
        target.setBillingState(source.getBillingState());
        target.setBillingPincode(source.getBillingPincode());
        target.setBillingCountry(source.getBillingCountry());
        target.setShippingAddressLine1(source.getShippingAddressLine1());
        target.setShippingAddressLine2(source.getShippingAddressLine2());
        target.setShippingCity(source.getShippingCity());
        target.setShippingState(source.getShippingState());
        target.setShippingPincode(source.getShippingPincode());
        target.setShippingCountry(source.getShippingCountry());
    }

    private void copyBank(VendorBankDetails source, VendorBankDetails target) {
        target.setAccountHolderName(source.getAccountHolderName());
        target.setBeneficiaryName(source.getBeneficiaryName());
        target.setBankName(source.getBankName());
        target.setAccountNumber(source.getAccountNumber());
        target.setIfscCode(source.getIfscCode());
        target.setBranchName(source.getBranchName());
        target.setAccountType(source.getAccountType());
        target.setSwiftCode(source.getSwiftCode());
        target.setIban(source.getIban());
        target.setBankCountry(source.getBankCountry());
        target.setBankAddress(source.getBankAddress());
        target.setUpiId(source.getUpiId());
        target.setNotes(source.getNotes());
    }

    private String uniqueCloneName(String sourceName) {
        String base = sourceName + " Copy";
        String candidate = base;
        int suffix = 2;
        while (vendorRepository.existsByOrganizationIdAndVendorNameIgnoreCase(ORGANIZATION_ID, candidate)) {
            candidate = base + " " + suffix++;
        }
        return candidate;
    }

    private String vendorAddress(VendorResponse vendor) {
        return Stream.of(
                        vendor.billingAddressLine1(),
                        vendor.billingAddressLine2(),
                        vendor.billingCity(),
                        vendor.billingState(),
                        vendor.billingPincode(),
                        vendor.billingCountry()
                )
                .filter(StringUtils::hasText)
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
    }

    private String mask(String accountNumber) {
        if (!StringUtils.hasText(accountNumber)) return "";
        String compact = accountNumber.replaceAll("\\s", "");
        String lastFour = compact.length() <= 4 ? compact : compact.substring(compact.length() - 4);
        return "XXXX XXXX " + lastFour;
    }

    private boolean contains(String value, String search) {
        return StringUtils.hasText(value) && value.toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT).trim());
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private String currency(BigDecimal value) {
        return "₹" + String.format(Locale.ENGLISH, "%,.2f", money(value));
    }

    private String formatDate(LocalDate date) {
        return date == null ? "" : date.format(DateTimeFormatter.ofPattern("dd MMM yyyy"));
    }

    private String clean(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String upper(String value) {
        String cleaned = clean(value);
        return cleaned == null ? null : cleaned.toUpperCase(Locale.ROOT);
    }

    private String lower(String value) {
        String cleaned = clean(value);
        return cleaned == null ? null : cleaned.toLowerCase(Locale.ROOT);
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}
