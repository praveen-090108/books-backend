package com.intelliatech.app.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intelliatech.app.dto.request.ExpenseRequest;
import com.intelliatech.app.dto.response.ExpenseFiltersResponse;
import com.intelliatech.app.dto.response.ExpenseResponse;
import com.intelliatech.app.dto.response.ExpenseSummaryResponse;
import com.intelliatech.app.dto.response.ExpenseTaxSummaryResponse;
import com.intelliatech.app.entity.BusinessRecord;
import com.intelliatech.app.entity.Expense;
import com.intelliatech.app.entity.ExpenseAccountMaster;
import com.intelliatech.app.entity.ExpenseStatus;
import com.intelliatech.app.entity.ExpenseType;
import com.intelliatech.app.entity.GstTreatment;
import com.intelliatech.app.entity.SupplyState;
import com.intelliatech.app.entity.TaxRate;
import com.intelliatech.app.entity.Vendor;
import com.intelliatech.app.entity.VendorStatus;
import com.intelliatech.app.exception.DuplicateResourceException;
import com.intelliatech.app.exception.ResourceNotFoundException;
import com.intelliatech.app.repository.BusinessRecordRepository;
import com.intelliatech.app.repository.ExpenseRepository;
import com.intelliatech.app.repository.ExpenseAccountMasterRepository;
import com.intelliatech.app.repository.SupplyStateRepository;
import com.intelliatech.app.repository.TaxRateRepository;
import com.intelliatech.app.repository.VendorRepository;
import com.intelliatech.app.service.ExpenseService;
import com.intelliatech.app.service.ExpenseTaxCalculator;
import com.intelliatech.app.service.BankAccountService;
import com.intelliatech.app.service.GstTreatmentRules;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ExpenseServiceImpl implements ExpenseService {
    private static final Long ORGANIZATION_ID = 1L;
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final List<String> PAYMENT_MODES = List.of(
            "Cash", "Bank Transfer", "UPI", "Credit Card", "Debit Card", "Cheque",
            "NEFT", "RTGS", "IMPS", "PayPal", "Wise", "Other");
    private static final Map<GstTreatment, String> GST_LABELS = Map.ofEntries(
            Map.entry(GstTreatment.REGISTERED_BUSINESS_REGULAR, "Registered Business - Regular"),
            Map.entry(GstTreatment.REGISTERED_BUSINESS_COMPOSITION, "Registered Business - Composition"),
            Map.entry(GstTreatment.UNREGISTERED_BUSINESS, "Unregistered Business"),
            Map.entry(GstTreatment.CONSUMER, "Consumer"),
            Map.entry(GstTreatment.OVERSEAS, "Overseas"),
            Map.entry(GstTreatment.SPECIAL_ECONOMIC_ZONE, "Special Economic Zone"),
            Map.entry(GstTreatment.DEEMED_EXPORT, "Deemed Export"),
            Map.entry(GstTreatment.TAX_DEDUCTOR, "Tax Deductor"),
            Map.entry(GstTreatment.TAX_COLLECTOR, "Tax Collector"));

    private final ExpenseRepository expenseRepository;
    private final ExpenseAccountMasterRepository expenseAccountMasterRepository;
    private final VendorRepository vendorRepository;
    private final TaxRateRepository taxRateRepository;
    private final SupplyStateRepository supplyStateRepository;
    private final BusinessRecordRepository businessRecordRepository;
    private final ExpenseTaxCalculator taxCalculator;
    private final GstTreatmentRules gstTreatmentRules;
    private final BankAccountService bankAccountService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public Page<ExpenseResponse> findAll(
            String search, Long vendorId, String expenseType, String gstTreatment,
            String sourceOfSupply, String destinationOfSupply, Long taxId, String amountType,
            String expenseAccount, String status, String paymentMode, LocalDate dateFrom,
            LocalDate dateTo, BigDecimal amountMin, BigDecimal amountMax, Pageable pageable) {
        return expenseRepository.findAll(specification(search, vendorId, expenseType, gstTreatment,
                sourceOfSupply, destinationOfSupply, taxId, amountType, expenseAccount, status,
                paymentMode, dateFrom, dateTo, amountMin, amountMax), pageable).map(this::toResponse);
    }

    @Override @Transactional(readOnly = true)
    public ExpenseResponse findById(Long id) { return toResponse(getExpense(id)); }

    @Override @Transactional
    public ExpenseResponse create(ExpenseRequest request) {
        Expense expense = new Expense();
        expense.setOrganizationId(ORGANIZATION_ID);
        expense.setExpenseNumber(temporaryExpenseNumber());
        copy(request, expense, null);
        Expense saved = expenseRepository.saveAndFlush(expense);
        saved.setExpenseNumber("EXP-%06d".formatted(saved.getId()));
        saved = expenseRepository.saveAndFlush(saved);
        syncLegacyRecord(saved);
        return toResponse(saved);
    }

    @Override @Transactional
    public ExpenseResponse update(Long id, ExpenseRequest request) {
        Expense expense = getExpense(id);
        copy(request, expense, id);
        Expense saved = expenseRepository.save(expense);
        syncLegacyRecord(saved);
        return toResponse(saved);
    }

    @Override @Transactional
    public void delete(Long id) {
        Expense expense = getExpense(id);
        expense.setDeleted(true);
        businessRecordRepository.findByRecordNumber(expense.getExpenseNumber())
                .ifPresent(businessRecordRepository::delete);
    }

    @Override @Transactional(readOnly = true)
    public ExpenseSummaryResponse summary() {
        List<Expense> expenses = expenseRepository.findAll(specification(null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null));
        BigDecimal total = ZERO;
        BigDecimal month = ZERO;
        BigDecimal year = ZERO;
        YearMonth currentMonth = YearMonth.now();
        Year currentYear = Year.now();
        for (Expense expense : expenses) {
            total = total.add(expense.getTotalAmount());
            if (YearMonth.from(expense.getExpenseDate()).equals(currentMonth)) month = month.add(expense.getTotalAmount());
            if (Year.from(expense.getExpenseDate()).equals(currentYear)) year = year.add(expense.getTotalAmount());
        }
        return new ExpenseSummaryResponse(money(total), money(month), money(year), expenses.size());
    }

    @Override @Transactional(readOnly = true)
    public ExpenseFiltersResponse filters(String gstTreatment, String source, String destination) {
        OrganizationTaxProfile organization = organizationTaxProfile();
        List<ExpenseFiltersResponse.StateOption> states = supplyStateRepository.findAllByOrderByNameAsc().stream()
                .map(state -> new ExpenseFiltersResponse.StateOption(state.getCode(), state.getName(), state.getName() + " (" + state.getCode() + ")"))
                .toList();
        GstTreatment treatment = parseEnum(GstTreatment.class, gstTreatment);
        List<ExpenseFiltersResponse.TaxOption> taxes = taxRateRepository.findByActiveTrueOrderByDisplayOrderAsc().stream()
                .filter(tax -> treatment != GstTreatment.OVERSEAS || tax.getRate().signum() == 0)
                .map(tax -> new ExpenseFiltersResponse.TaxOption(tax.getId(), tax.getCode(), tax.getName(),
                        taxDisplayName(tax, treatment, source, destination), tax.getRate(), tax.getTaxCategory()))
                .toList();
        List<ExpenseFiltersResponse.Option> treatments = GST_LABELS.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .map(entry -> new ExpenseFiltersResponse.Option(entry.getKey().name(), entry.getValue(),
                        gstTreatmentRules.requiresSourceOfSupply(entry.getKey())))
                .toList();
        List<String> expenseAccounts = expenseAccountMasterRepository
                .findAllByOrganizationIdAndActiveTrueOrderByDisplayOrderAscAccountNameAsc(ORGANIZATION_ID).stream()
                .map(ExpenseAccountMaster::getAccountName).toList();
        return new ExpenseFiltersResponse(treatments, states, taxes, expenseAccounts, PAYMENT_MODES,
                organization.stateCode(), organization.stateName(), organization.country());
    }

    private void copy(ExpenseRequest request, Expense expense, Long existingId) {
        Vendor vendor = request.vendorId() == null ? null : vendorRepository.findByIdAndOrganizationId(request.vendorId(), ORGANIZATION_ID)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor not found."));
        if (vendor != null && vendor.getStatus() != VendorStatus.ACTIVE) throw new IllegalArgumentException("Only active vendors can be selected.");
        String invoiceNumber = clean(request.invoiceNumber());
        if (StringUtils.hasText(invoiceNumber) && vendor != null) {
            boolean duplicate = existingId == null
                    ? expenseRepository.existsByOrganizationIdAndVendorIdAndInvoiceNumberIgnoreCaseAndDeletedFalse(
                            ORGANIZATION_ID, vendor.getId(), invoiceNumber)
                    : expenseRepository.existsByOrganizationIdAndVendorIdAndInvoiceNumberIgnoreCaseAndIdNotAndDeletedFalse(
                            ORGANIZATION_ID, vendor.getId(), invoiceNumber, existingId);
            if (duplicate) throw new DuplicateResourceException("An expense with this invoice number already exists for the selected vendor.");
        }
        if (vendor != null && request.gstTreatment() == GstTreatment.REGISTERED_BUSINESS_REGULAR && !StringUtils.hasText(vendor.getGstin())) {
            throw new IllegalArgumentException("Vendor GSTIN is required for Registered Business - Regular.");
        }
        SupplyState source = getOptionalState(request.sourceOfSupplyCode(), "Source of Supply");
        if (gstTreatmentRules.requiresSourceOfSupply(request.gstTreatment()) && source == null) {
            throw new IllegalArgumentException("Source of Supply is required for the selected GST Treatment.");
        }
        SupplyState destination = getState(request.destinationOfSupplyCode(), "Destination of Supply");
        TaxRate tax = request.taxId() == null ? null : taxRateRepository.findById(request.taxId())
                .filter(TaxRate::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Tax rate not found."));
        if (request.gstTreatment() != GstTreatment.OVERSEAS && tax == null) {
            throw new IllegalArgumentException("Tax is required unless the expense is overseas.");
        }
        ExpenseTaxSummaryResponse totals = taxCalculator.calculate(request.amount(), request.amountType(),
                request.gstTreatment(), source == null ? null : source.getCode(), destination.getCode(), tax);

        expense.setExpenseDate(request.expenseDate());
        ExpenseAccountMaster account = request.expenseAccountId() != null
                ? expenseAccountMasterRepository.findByIdAndOrganizationId(request.expenseAccountId(), ORGANIZATION_ID)
                    .orElseThrow(() -> new ResourceNotFoundException("Expense Account not found."))
                : expenseAccountMasterRepository.findByOrganizationIdAndAccountNameIgnoreCase(ORGANIZATION_ID, clean(request.expenseAccount()))
                    .orElseThrow(() -> new ResourceNotFoundException("Expense Account not found."));
        boolean retainingInactive = existingId != null && expense.getExpenseAccountMaster() != null
                && expense.getExpenseAccountMaster().getId().equals(account.getId());
        if (!account.isActive() && !retainingInactive) throw new IllegalArgumentException("Selected Expense Account is inactive.");
        expense.setExpenseAccountMaster(account);
        expense.setExpenseAccount(account.getAccountName());
        expense.setExpenseTitle(clean(request.expenseTitle()));
        expense.setExpenseType(request.expenseType());
        expense.setVendor(vendor);
        expense.setInvoiceNumber(invoiceNumber);
        expense.setHsnCode(request.expenseType() == ExpenseType.GOODS ? clean(request.hsnCode()) : null);
        expense.setSacCode(request.expenseType() == ExpenseType.SERVICES ? clean(request.sacCode()) : null);
        expense.setGstTreatment(request.gstTreatment());
        expense.setSourceOfSupply(source);
        expense.setDestinationOfSupply(destination);
        expense.setTax(tax);
        expense.setTaxName(tax == null ? null : taxDisplayName(tax, request.gstTreatment(), source == null ? null : source.getCode(), destination.getCode()));
        expense.setTaxRate(totals.taxRate());
        expense.setAmountType(request.amountType());
        expense.setEnteredAmount(totals.enteredAmount());
        expense.setTaxableAmount(totals.taxableAmount());
        expense.setCgstAmount(totals.cgstAmount());
        expense.setSgstAmount(totals.sgstAmount());
        expense.setIgstAmount(totals.igstAmount());
        expense.setCessAmount(totals.cessAmount());
        expense.setTotalTaxAmount(totals.totalTaxAmount());
        expense.setTotalAmount(totals.totalAmount());
        expense.setTdsDeducted(money(request.tdsDeducted()));
        expense.setCurrency(clean(request.currency()));
        expense.setReferenceNumber(clean(request.referenceNumber()));
        expense.setDescription(clean(request.description()));
        expense.setNotes(clean(request.notes()));
        expense.setPaymentMode(clean(request.paymentMode()));
        Long retainedBankAccountId = expense.getBankAccount() == null ? null : expense.getBankAccount().getId();
        var bankAccount = bankAccountService.selection(request.bankAccountId(), retainedBankAccountId);
        if (bankAccountService.requiresAccount(request.paymentMode()) && bankAccount == null) {
            throw new IllegalArgumentException("Bank Account is required for the selected Payment Mode.");
        }
        expense.setBankAccount(bankAccount);
        expense.setPaidThrough(bankAccount == null ? clean(request.paidThrough()) : bankAccountService.displayName(bankAccount));
        expense.setProjectName(clean(request.projectName()));
        expense.setStatus(request.status());
        expense.setAttachmentName(clean(request.attachmentName()));
        expense.setAttachmentUrl(clean(request.attachmentUrl()));
    }

    private ExpenseResponse toResponse(Expense expense) {
        ExpenseTaxSummaryResponse taxSummary = new ExpenseTaxSummaryResponse(
                expense.getEnteredAmount(), expense.getTaxableAmount(), expense.getTaxRate(),
                taxMode(expense), expense.getCgstAmount(), expense.getSgstAmount(), expense.getIgstAmount(),
                expense.getCessAmount(), expense.getTotalTaxAmount(), expense.getTotalAmount());
        return new ExpenseResponse(expense.getId(), expense.getExpenseNumber(), expense.getExpenseDate(),
                expense.getExpenseAccount(), expense.getExpenseTitle(), expense.getExpenseType(),
                expense.getVendor() == null ? null : expense.getVendor().getId(),
                expense.getVendor() == null ? "" : expense.getVendor().getVendorName(),
                expense.getVendor() == null ? "" : expense.getVendor().getGstin(), expense.getInvoiceNumber(),
                expense.getHsnCode(), expense.getSacCode(), expense.getGstTreatment(),
                expense.getSourceOfSupply() == null ? null : expense.getSourceOfSupply().getCode(),
                expense.getSourceOfSupply() == null ? "" : expense.getSourceOfSupply().getName(),
                expense.getDestinationOfSupply() == null ? null : expense.getDestinationOfSupply().getCode(),
                expense.getDestinationOfSupply() == null ? "" : expense.getDestinationOfSupply().getName(),
                expense.getTax() == null ? null : expense.getTax().getId(), expense.getTaxName(),
                expense.getAmountType(), taxSummary, expense.getTdsDeducted(), expense.getCurrency(), expense.getReferenceNumber(),
                expense.getDescription(), expense.getNotes(), expense.getPaymentMode(), expense.getPaidThrough(),
                expense.getBankAccount() == null ? null : expense.getBankAccount().getId(),
                expense.getBankAccount() == null ? expense.getPaidThrough() : expense.getBankAccount().getAccountName(),
                expense.getProjectName(), expense.getStatus(), expense.getAttachmentName(), expense.getAttachmentUrl(),
                expense.getCreatedBy(), expense.getCreatedAt(), expense.getUpdatedAt());
    }

    private Specification<Expense> specification(
            String search, Long vendorId, String expenseType, String gstTreatment, String source,
            String destination, Long taxId, String amountType, String account, String status,
            String paymentMode, LocalDate dateFrom, LocalDate dateTo, BigDecimal amountMin, BigDecimal amountMax) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("organizationId"), ORGANIZATION_ID));
            predicates.add(builder.isFalse(root.get("deleted")));
            if (StringUtils.hasText(search)) {
                String term = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("expenseNumber")), term),
                        builder.like(builder.lower(root.get("expenseTitle")), term),
                        builder.like(builder.lower(root.get("invoiceNumber")), term),
                        builder.like(builder.lower(root.get("referenceNumber")), term),
                        builder.like(builder.lower(root.get("description")), term),
                        builder.like(builder.lower(root.join("vendor").get("vendorName")), term)));
            }
            if (vendorId != null) predicates.add(builder.equal(root.get("vendor").get("id"), vendorId));
            enumPredicate(predicates, builder, root.get("expenseType"), expenseType, ExpenseType.class);
            enumPredicate(predicates, builder, root.get("gstTreatment"), gstTreatment, GstTreatment.class);
            enumPredicate(predicates, builder, root.get("amountType"), amountType, com.intelliatech.app.entity.ExpenseAmountType.class);
            enumPredicate(predicates, builder, root.get("status"), status, ExpenseStatus.class);
            if (StringUtils.hasText(source)) predicates.add(builder.equal(root.get("sourceOfSupply").get("code"), source));
            if (StringUtils.hasText(destination)) predicates.add(builder.equal(root.get("destinationOfSupply").get("code"), destination));
            if (taxId != null) predicates.add(builder.equal(root.get("tax").get("id"), taxId));
            if (StringUtils.hasText(account)) predicates.add(builder.equal(root.get("expenseAccount"), account));
            if (StringUtils.hasText(paymentMode)) predicates.add(builder.equal(root.get("paymentMode"), paymentMode));
            if (dateFrom != null) predicates.add(builder.greaterThanOrEqualTo(root.get("expenseDate"), dateFrom));
            if (dateTo != null) predicates.add(builder.lessThanOrEqualTo(root.get("expenseDate"), dateTo));
            if (amountMin != null) predicates.add(builder.greaterThanOrEqualTo(root.get("totalAmount"), amountMin));
            if (amountMax != null) predicates.add(builder.lessThanOrEqualTo(root.get("totalAmount"), amountMax));
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private <E extends Enum<E>> void enumPredicate(List<Predicate> predicates, jakarta.persistence.criteria.CriteriaBuilder builder,
            jakarta.persistence.criteria.Path<E> path, String value, Class<E> type) {
        E parsed = parseEnum(type, value);
        if (parsed != null) predicates.add(builder.equal(path, parsed));
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
        if (!StringUtils.hasText(value)) return null;
        try { return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException("Unsupported " + type.getSimpleName() + " value."); }
    }

    private Expense getExpense(Long id) {
        return expenseRepository.findByIdAndOrganizationIdAndDeletedFalse(id, ORGANIZATION_ID)
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found."));
    }

    private SupplyState getState(String code, String label) {
        return supplyStateRepository.findById(code)
                .orElseThrow(() -> new IllegalArgumentException(label + " is not valid."));
    }

    private SupplyState getOptionalState(String code, String label) {
        if (!StringUtils.hasText(code)) return null;
        return getState(code, label);
    }

    private String temporaryExpenseNumber() {
        String token = UUID.randomUUID().toString().replace("-", "");
        return "TMP-" + token.substring(0, 20);
    }

    private void syncLegacyRecord(Expense expense) {
        BusinessRecord record = businessRecordRepository.findByRecordNumber(expense.getExpenseNumber()).orElseGet(BusinessRecord::new);
        record.setModule("purchases");
        record.setType("expenses");
        record.setRecordNumber(expense.getExpenseNumber());
        // BusinessRecord is a legacy reporting mirror whose partyName column is
        // non-null. Do not create a dummy Vendor merely to satisfy that legacy
        // constraint; retain an explicit display snapshot for vendor-less expenses.
        record.setPartyName(expense.getVendor() == null ? "Vendor not specified" : expense.getVendor().getVendorName());
        record.setPartyEmail(expense.getVendor() == null ? "" : expense.getVendor().getEmail());
        record.setPartyPhone(expense.getVendor() == null ? "" : expense.getVendor().getPhone());
        record.setPartyCity(expense.getSourceOfSupply() == null ? "" : expense.getSourceOfSupply().getName());
        record.setCategory(expense.getExpenseAccount());
        record.setStatus(displayEnum(expense.getStatus().name()));
        record.setSecondaryStatus(displayEnum(expense.getExpenseType().name()));
        record.setAmount(expense.getTotalAmount());
        record.setBalanceAmount(expense.getStatus() == ExpenseStatus.PAID ? ZERO : expense.getTotalAmount());
        record.setRecordDate(expense.getExpenseDate());
        record.setDueDate(expense.getExpenseDate());
        record.setReferenceNumber(expense.getInvoiceNumber());
        record.setPaymentMode(expense.getPaymentMode());
        record.setOwnerName(expense.getCreatedBy());
        record.setNotes(expense.getDescription());
        businessRecordRepository.save(record);
    }

    private OrganizationTaxProfile organizationTaxProfile() {
        BusinessRecord record = businessRecordRepository.findFirstByModuleAndTypeOrderByRecordDateDesc("settings", "organization")
                .orElse(null);
        String stateValue = "";
        String country = "India";
        if (record != null && StringUtils.hasText(record.getNotes())) {
            try {
                JsonNode notes = objectMapper.readTree(record.getNotes());
                stateValue = notes.path("state").asText("");
                country = notes.path("country").asText("India");
            } catch (Exception ignored) { stateValue = ""; }
        }
        String stateCode = extractStateCode(stateValue);
        SupplyState state = stateCode == null ? supplyStateRepository.findFirstByNameIgnoreCase(stripStateCode(stateValue)).orElse(null)
                : supplyStateRepository.findById(stateCode).orElse(null);
        return new OrganizationTaxProfile(state == null ? null : state.getCode(), state == null ? "" : state.getName(), country);
    }

    private String taxDisplayName(TaxRate tax, GstTreatment treatment, String source, String destination) {
        if (!"TAXABLE".equalsIgnoreCase(tax.getTaxCategory()) || tax.getRate().signum() == 0) return tax.getName();
        if (treatment == GstTreatment.OVERSEAS) return tax.getName();
        if (treatment == GstTreatment.SPECIAL_ECONOMIC_ZONE || treatment == GstTreatment.DEEMED_EXPORT
                || (StringUtils.hasText(source) && StringUtils.hasText(destination) && !source.equals(destination))) {
            return "IGST " + decimalRate(tax.getRate()) + "%";
        }
        BigDecimal half = tax.getRate().divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);
        return "GST " + decimalRate(tax.getRate()) + "% (CGST " + decimalRate(half) + "% + SGST " + decimalRate(half) + "%)";
    }

    private String taxMode(Expense expense) {
        if (expense.getTotalTaxAmount().signum() == 0) return "NONE";
        return expense.getIgstAmount().signum() > 0 ? "IGST" : "CGST_SGST";
    }

    private String extractStateCode(String value) {
        if (!StringUtils.hasText(value)) return null;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?:\\(|^)(\\d{2})(?:\\)|$)").matcher(value);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String stripStateCode(String value) { return value == null ? "" : value.replaceAll("\\s*\\(\\d{2}\\)\\s*$", "").trim(); }
    private String clean(String value) { return StringUtils.hasText(value) ? value.trim() : null; }
    private BigDecimal money(BigDecimal value) { return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP); }
    private String decimalRate(BigDecimal rate) { return rate.stripTrailingZeros().toPlainString(); }
    private String displayEnum(String value) {
        String text = value.replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
    private record OrganizationTaxProfile(String stateCode, String stateName, String country) {}
}
