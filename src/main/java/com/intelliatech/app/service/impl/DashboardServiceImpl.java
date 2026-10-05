package com.intelliatech.app.service.impl;

import com.intelliatech.app.dto.response.BusinessRecordResponse;
import com.intelliatech.app.dto.response.DashboardSummaryResponse;
import com.intelliatech.app.dto.response.MonthlyTotalResponse;
import com.intelliatech.app.dto.response.OverviewCategoryTotalResponse;
import com.intelliatech.app.dto.response.OverviewDocumentResponse;
import com.intelliatech.app.dto.response.OverviewPartyTotalResponse;
import com.intelliatech.app.dto.response.PurchaseOverviewResponse;
import com.intelliatech.app.dto.response.SalesOverviewResponse;
import com.intelliatech.app.entity.Bill;
import com.intelliatech.app.entity.BillStatus;
import com.intelliatech.app.entity.BusinessRecord;
import com.intelliatech.app.entity.Expense;
import com.intelliatech.app.entity.InvoiceLifecycle;
import com.intelliatech.app.entity.InvoiceStatus;
import com.intelliatech.app.entity.PurchaseOrder;
import com.intelliatech.app.entity.Vendor;
import com.intelliatech.app.mapper.BusinessRecordMapper;
import com.intelliatech.app.repository.BillRepository;
import com.intelliatech.app.repository.BusinessRecordRepository;
import com.intelliatech.app.repository.ExpenseRepository;
import com.intelliatech.app.repository.InvoiceLifecycleRepository;
import com.intelliatech.app.repository.PurchaseOrderRepository;
import com.intelliatech.app.repository.VendorRepository;
import com.intelliatech.app.service.DashboardService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private static final Long ORGANIZATION_ID = 1L;
    private static final Set<String> TRANSACTION_TYPES = Set.of(
            "invoices", "quotes", "sales-orders", "payments-received",
            "bills", "expenses", "purchase-orders"
    );

    private final BusinessRecordRepository businessRecordRepository;
    private final BusinessRecordMapper businessRecordMapper;
    private final InvoiceLifecycleRepository invoiceLifecycleRepository;
    private final BillRepository billRepository;
    private final ExpenseRepository expenseRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final VendorRepository vendorRepository;

    @Override
    @Transactional(readOnly = true)
    public DashboardSummaryResponse getSummary(LocalDate dateFrom, LocalDate dateTo) {
        DateRange range = resolveRange(dateFrom, dateTo);
        SalesOverviewResponse sales = buildSalesOverview(range);
        PurchaseOverviewResponse purchases = buildPurchaseOverview(range);

        List<BusinessRecordResponse> recent = businessRecordRepository
                .findByRecordDateBetween(range.from(), range.to())
                .stream()
                .filter(record -> TRANSACTION_TYPES.contains(record.getType()))
                .sorted(Comparator.comparing(BusinessRecord::getRecordDate)
                        .thenComparing(BusinessRecord::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .reversed())
                .limit(8)
                .map(businessRecordMapper::toResponse)
                .toList();

        BigDecimal cashFlow = money(sales.totalSales()).subtract(money(purchases.totalPurchases()));
        return new DashboardSummaryResponse(
                range.from(),
                range.to(),
                sales.outstandingReceivables(),
                purchases.pendingBillAmount(),
                cashFlow,
                cashFlow,
                sales.totalSales(),
                purchases.totalPurchases(),
                sales.totalInvoices(),
                sales.paidInvoices(),
                sales.overdueInvoices(),
                sales.totalCustomers(),
                purchases.totalVendors(),
                sales.totalInvoices(),
                purchases.totalBillCount() + purchases.totalExpenseCount() + purchases.totalPurchaseOrders(),
                sales.currentTrend(),
                purchases.currentTrend(),
                recent
        );
    }

    @Override
    @Transactional(readOnly = true)
    public SalesOverviewResponse getSalesOverview(LocalDate dateFrom, LocalDate dateTo) {
        return buildSalesOverview(resolveRange(dateFrom, dateTo));
    }

    @Override
    @Transactional(readOnly = true)
    public PurchaseOverviewResponse getPurchaseOverview(LocalDate dateFrom, LocalDate dateTo) {
        return buildPurchaseOverview(resolveRange(dateFrom, dateTo));
    }

    private SalesOverviewResponse buildSalesOverview(DateRange range) {
        List<BusinessRecord> allInRange = businessRecordRepository.findByRecordDateBetween(range.from(), range.to());
        List<BusinessRecord> invoices = allInRange.stream()
                .filter(record -> "sales".equalsIgnoreCase(record.getModule()))
                .filter(record -> "invoices".equalsIgnoreCase(record.getType()))
                .toList();
        Map<Long, InvoiceLifecycle> lifecycles = lifecycleMap(invoices);

        BigDecimal totalSales = sum(invoices, BusinessRecord::getAmount);
        BigDecimal outstanding = invoices.stream()
                .map(invoice -> invoiceBalance(invoice, lifecycles.get(invoice.getId())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paidAmount = totalSales.subtract(outstanding).max(BigDecimal.ZERO);
        long paidInvoices = invoices.stream()
                .filter(invoice -> invoiceStatus(invoice, lifecycles.get(invoice.getId())) == InvoiceStatus.PAID)
                .count();
        List<BusinessRecord> overdue = invoices.stream()
                .filter(invoice -> isOverdue(invoice, lifecycles.get(invoice.getId()), range.to()))
                .toList();
        BigDecimal overdueAmount = overdue.stream()
                .map(invoice -> invoiceBalance(invoice, lifecycles.get(invoice.getId())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<BusinessRecord> customers = allInRange.stream()
                .filter(record -> "sales".equalsIgnoreCase(record.getModule()))
                .filter(record -> "customers".equalsIgnoreCase(record.getType()))
                .toList();
        long totalCustomers = customers.size();
        if (totalCustomers == 0) {
            totalCustomers = businessRecordRepository.findAll().stream()
                    .filter(record -> "sales".equalsIgnoreCase(record.getModule()))
                    .filter(record -> "customers".equalsIgnoreCase(record.getType()))
                    .count();
        }

        List<OverviewPartyTotalResponse> topCustomers = groupPartyTotals(
                invoices.stream().collect(Collectors.groupingBy(this::partyName)),
                BusinessRecord::getAmount,
                5
        );
        List<OverviewCategoryTotalResponse> channels = groupCategoryTotals(
                invoices.stream().collect(Collectors.groupingBy(invoice -> textOrDefault(invoice.getCategory(), "Direct Sales"))),
                BusinessRecord::getAmount
        );
        List<OverviewCategoryTotalResponse> aging = agingSummary(invoices, lifecycles, range.to());
        List<OverviewDocumentResponse> recent = invoices.stream()
                .sorted(Comparator.comparing(BusinessRecord::getRecordDate).reversed()
                        .thenComparing(BusinessRecord::getId, Comparator.reverseOrder()))
                .limit(5)
                .map(invoice -> invoiceDocument(invoice, lifecycles.get(invoice.getId())))
                .toList();

        DateRange previous = range.previousYear();
        List<BusinessRecord> previousInvoices = salesInvoices(previous);
        return new SalesOverviewResponse(
                range.from(),
                range.to(),
                totalSales,
                invoices.size(),
                invoices.isEmpty() ? BigDecimal.ZERO : totalSales.divide(BigDecimal.valueOf(invoices.size()), 2, RoundingMode.HALF_UP),
                paidInvoices,
                paidAmount,
                outstanding,
                overdue.size(),
                overdueAmount,
                totalCustomers,
                monthlyBusinessTotals(invoices, range),
                monthlyBusinessTotals(previousInvoices, previous),
                topCustomers,
                recent,
                channels,
                aging
        );
    }

    private PurchaseOverviewResponse buildPurchaseOverview(DateRange range) {
        List<Bill> bills = billRepository.findAllByOrganizationIdAndDeletedFalse(ORGANIZATION_ID).stream()
                .filter(bill -> inRange(bill.getBillDate(), range))
                .toList();
        List<Expense> expenses = expenseRepository.findAll().stream()
                .filter(expense -> ORGANIZATION_ID.equals(expense.getOrganizationId()) && !expense.isDeleted())
                .filter(expense -> inRange(expense.getExpenseDate(), range))
                .toList();
        List<PurchaseOrder> purchaseOrders = purchaseOrderRepository
                .findAllByOrganizationIdAndDeletedFalse(ORGANIZATION_ID).stream()
                .filter(order -> inRange(order.getPurchaseOrderDate(), range))
                .toList();
        List<Vendor> vendors = vendorRepository.findAll().stream()
                .filter(vendor -> ORGANIZATION_ID.equals(vendor.getOrganizationId()))
                .toList();

        BigDecimal billTotal = sum(bills, Bill::getTotalAmount);
        BigDecimal expenseTotal = sum(expenses, Expense::getTotalAmount);
        List<Bill> pendingBills = bills.stream()
                .filter(bill -> Set.of(BillStatus.OPEN, BillStatus.PARTIALLY_PAID, BillStatus.OVERDUE).contains(bill.getStatus()))
                .toList();
        BigDecimal pendingAmount = sum(pendingBills, Bill::getBalanceDue);
        long newVendors = vendors.stream()
                .filter(vendor -> vendor.getCreatedAt() != null)
                .filter(vendor -> inRange(vendor.getCreatedAt().toLocalDate(), range))
                .count();

        Map<String, List<PurchaseAmount>> vendorAmounts = new HashMap<>();
        bills.forEach(bill -> vendorAmounts.computeIfAbsent(textOrDefault(bill.getVendorName(), "Unassigned"), ignored -> new ArrayList<>())
                .add(new PurchaseAmount(bill.getTotalAmount())));
        expenses.forEach(expense -> vendorAmounts.computeIfAbsent(expenseVendorName(expense), ignored -> new ArrayList<>())
                .add(new PurchaseAmount(expense.getTotalAmount())));
        List<OverviewPartyTotalResponse> topVendors = vendorAmounts.entrySet().stream()
                .map(entry -> new OverviewPartyTotalResponse(
                        entry.getKey(),
                        sum(entry.getValue(), PurchaseAmount::amount),
                        entry.getValue().size()))
                .sorted(Comparator.comparing(OverviewPartyTotalResponse::total).reversed())
                .limit(5)
                .toList();

        Map<String, List<PurchaseAmount>> categoryAmounts = new HashMap<>();
        bills.forEach(bill -> categoryAmounts.computeIfAbsent("Bills", ignored -> new ArrayList<>())
                .add(new PurchaseAmount(bill.getTotalAmount())));
        expenses.forEach(expense -> categoryAmounts.computeIfAbsent(textOrDefault(expense.getExpenseAccount(), "Other Expenses"), ignored -> new ArrayList<>())
                .add(new PurchaseAmount(expense.getTotalAmount())));
        List<OverviewCategoryTotalResponse> categories = categoryAmounts.entrySet().stream()
                .map(entry -> new OverviewCategoryTotalResponse(
                        entry.getKey(),
                        sum(entry.getValue(), PurchaseAmount::amount),
                        entry.getValue().size()))
                .sorted(Comparator.comparing(OverviewCategoryTotalResponse::total).reversed())
                .toList();

        List<OverviewDocumentResponse> recentBills = bills.stream()
                .sorted(Comparator.comparing(Bill::getBillDate).reversed().thenComparing(Bill::getId, Comparator.reverseOrder()))
                .limit(5)
                .map(this::billDocument)
                .toList();
        DateRange previous = range.previousYear();
        List<Bill> previousBills = billRepository.findAllByOrganizationIdAndDeletedFalse(ORGANIZATION_ID).stream()
                .filter(bill -> inRange(bill.getBillDate(), previous))
                .toList();
        List<Expense> previousExpenses = expenseRepository.findAll().stream()
                .filter(expense -> ORGANIZATION_ID.equals(expense.getOrganizationId()) && !expense.isDeleted())
                .filter(expense -> inRange(expense.getExpenseDate(), previous))
                .toList();

        return new PurchaseOverviewResponse(
                range.from(),
                range.to(),
                billTotal.add(expenseTotal),
                billTotal,
                bills.size(),
                expenseTotal,
                expenses.size(),
                vendors.size(),
                newVendors,
                pendingAmount,
                pendingBills.size(),
                purchaseOrders.size(),
                monthlyPurchaseTotals(bills, expenses, range),
                monthlyPurchaseTotals(previousBills, previousExpenses, previous),
                topVendors,
                recentBills,
                categories
        );
    }

    private List<BusinessRecord> salesInvoices(DateRange range) {
        return businessRecordRepository.findByRecordDateBetween(range.from(), range.to()).stream()
                .filter(record -> "sales".equalsIgnoreCase(record.getModule()))
                .filter(record -> "invoices".equalsIgnoreCase(record.getType()))
                .toList();
    }

    private Map<Long, InvoiceLifecycle> lifecycleMap(Collection<BusinessRecord> invoices) {
        if (invoices.isEmpty()) return Map.of();
        return invoiceLifecycleRepository.findAllByInvoiceIdIn(invoices.stream().map(BusinessRecord::getId).toList())
                .stream()
                .collect(Collectors.toMap(lifecycle -> lifecycle.getInvoice().getId(), Function.identity()));
    }

    private InvoiceStatus invoiceStatus(BusinessRecord invoice, InvoiceLifecycle lifecycle) {
        if (lifecycle != null) return lifecycle.getStatus();
        try {
            return InvoiceStatus.valueOf(textOrDefault(invoice.getStatus(), "DRAFT").trim().toUpperCase(Locale.ENGLISH).replace(' ', '_'));
        } catch (IllegalArgumentException ignored) {
            return InvoiceStatus.DRAFT;
        }
    }

    private BigDecimal invoiceBalance(BusinessRecord invoice, InvoiceLifecycle lifecycle) {
        return money(lifecycle == null ? invoice.getBalanceAmount() : lifecycle.getBalanceDue());
    }

    private boolean isOverdue(BusinessRecord invoice, InvoiceLifecycle lifecycle, LocalDate asOf) {
        InvoiceStatus status = invoiceStatus(invoice, lifecycle);
        if (status == InvoiceStatus.OVERDUE) return invoiceBalance(invoice, lifecycle).signum() > 0;
        return invoice.getDueDate() != null
                && invoice.getDueDate().isBefore(asOf)
                && invoiceBalance(invoice, lifecycle).signum() > 0
                && !Set.of(InvoiceStatus.DRAFT, InvoiceStatus.PAID, InvoiceStatus.VOID).contains(status);
    }

    private OverviewDocumentResponse invoiceDocument(BusinessRecord invoice, InvoiceLifecycle lifecycle) {
        return new OverviewDocumentResponse(
                invoice.getId(), invoice.getRecordNumber(), partyName(invoice), invoice.getRecordDate(), invoice.getDueDate(),
                money(invoice.getAmount()), invoiceBalance(invoice, lifecycle), invoiceStatus(invoice, lifecycle).displayName()
        );
    }

    private OverviewDocumentResponse billDocument(Bill bill) {
        return new OverviewDocumentResponse(
                bill.getId(), bill.getBillNumber(), textOrDefault(bill.getVendorName(), "Unassigned"), bill.getBillDate(), bill.getDueDate(),
                money(bill.getTotalAmount()), money(bill.getBalanceDue()), displayStatus(bill.getStatus().name())
        );
    }

    private List<OverviewCategoryTotalResponse> agingSummary(
            List<BusinessRecord> invoices,
            Map<Long, InvoiceLifecycle> lifecycles,
            LocalDate asOf
    ) {
        Map<String, List<BigDecimal>> buckets = new LinkedHashMap<>();
        buckets.put("0 - 30 Days", new ArrayList<>());
        buckets.put("31 - 60 Days", new ArrayList<>());
        buckets.put("61 - 90 Days", new ArrayList<>());
        buckets.put("90+ Days", new ArrayList<>());
        invoices.forEach(invoice -> {
            BigDecimal balance = invoiceBalance(invoice, lifecycles.get(invoice.getId()));
            if (balance.signum() <= 0 || invoice.getDueDate() == null) return;
            long age = Math.max(0, ChronoUnit.DAYS.between(invoice.getDueDate(), asOf));
            String bucket = age <= 30 ? "0 - 30 Days" : age <= 60 ? "31 - 60 Days" : age <= 90 ? "61 - 90 Days" : "90+ Days";
            buckets.get(bucket).add(balance);
        });
        return buckets.entrySet().stream()
                .map(entry -> new OverviewCategoryTotalResponse(entry.getKey(), sum(entry.getValue(), Function.identity()), entry.getValue().size()))
                .toList();
    }

    private List<MonthlyTotalResponse> monthlyBusinessTotals(List<BusinessRecord> records, DateRange range) {
        return monthlyTotals(range, records.stream().collect(Collectors.groupingBy(
                record -> YearMonth.from(record.getRecordDate()),
                Collectors.reducing(BigDecimal.ZERO, record -> money(record.getAmount()), BigDecimal::add)
        )));
    }

    private List<MonthlyTotalResponse> monthlyPurchaseTotals(List<Bill> bills, List<Expense> expenses, DateRange range) {
        Map<YearMonth, BigDecimal> totals = new HashMap<>();
        bills.forEach(bill -> totals.merge(YearMonth.from(bill.getBillDate()), money(bill.getTotalAmount()), BigDecimal::add));
        expenses.forEach(expense -> totals.merge(YearMonth.from(expense.getExpenseDate()), money(expense.getTotalAmount()), BigDecimal::add));
        return monthlyTotals(range, totals);
    }

    private List<MonthlyTotalResponse> monthlyTotals(DateRange range, Map<YearMonth, BigDecimal> values) {
        List<MonthlyTotalResponse> totals = new ArrayList<>();
        YearMonth current = YearMonth.from(range.from());
        YearMonth end = YearMonth.from(range.to());
        while (!current.isAfter(end)) {
            String label = current.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
            if (YearMonth.from(range.from()).getYear() != end.getYear()) label += " " + String.valueOf(current.getYear()).substring(2);
            totals.add(new MonthlyTotalResponse(label, money(values.get(current))));
            current = current.plusMonths(1);
        }
        return totals;
    }

    private <T> List<OverviewPartyTotalResponse> groupPartyTotals(
            Map<String, List<T>> groups,
            Function<T, BigDecimal> value,
            int limit
    ) {
        return groups.entrySet().stream()
                .map(entry -> new OverviewPartyTotalResponse(entry.getKey(), sum(entry.getValue(), value), entry.getValue().size()))
                .sorted(Comparator.comparing(OverviewPartyTotalResponse::total).reversed())
                .limit(limit)
                .toList();
    }

    private <T> List<OverviewCategoryTotalResponse> groupCategoryTotals(
            Map<String, List<T>> groups,
            Function<T, BigDecimal> value
    ) {
        return groups.entrySet().stream()
                .map(entry -> new OverviewCategoryTotalResponse(entry.getKey(), sum(entry.getValue(), value), entry.getValue().size()))
                .sorted(Comparator.comparing(OverviewCategoryTotalResponse::total).reversed())
                .toList();
    }

    private <T> BigDecimal sum(Collection<T> values, Function<T, BigDecimal> mapper) {
        return values.stream().map(mapper).map(this::money).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String partyName(BusinessRecord record) {
        return textOrDefault(record.getPartyName(), "Unassigned");
    }

    private String expenseVendorName(Expense expense) {
        return expense.getVendor() == null ? "Unassigned" : textOrDefault(expense.getVendor().getVendorName(), "Unassigned");
    }

    private String textOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String displayStatus(String status) {
        String normalized = status.toLowerCase(Locale.ENGLISH).replace('_', ' ');
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private boolean inRange(LocalDate date, DateRange range) {
        return date != null && !date.isBefore(range.from()) && !date.isAfter(range.to());
    }

    private DateRange resolveRange(LocalDate dateFrom, LocalDate dateTo) {
        if (dateFrom == null && dateTo == null) {
            LocalDate today = LocalDate.now();
            int startYear = today.getMonthValue() < 4 ? today.getYear() - 1 : today.getYear();
            dateFrom = LocalDate.of(startYear, 4, 1);
            dateTo = dateFrom.plusYears(1).minusDays(1);
        } else if (dateFrom == null) {
            int startYear = dateTo.getMonthValue() < 4 ? dateTo.getYear() - 1 : dateTo.getYear();
            dateFrom = LocalDate.of(startYear, 4, 1);
        } else if (dateTo == null) {
            int startYear = dateFrom.getMonthValue() < 4 ? dateFrom.getYear() - 1 : dateFrom.getYear();
            dateTo = LocalDate.of(startYear + 1, 3, 31);
        }
        if (dateFrom.isAfter(dateTo)) throw new IllegalArgumentException("Start date must be on or before end date.");
        return new DateRange(dateFrom, dateTo);
    }

    private record DateRange(LocalDate from, LocalDate to) {
        DateRange previousYear() {
            return new DateRange(from.minusYears(1), to.minusYears(1));
        }
    }

    private record PurchaseAmount(BigDecimal amount) {
    }
}
