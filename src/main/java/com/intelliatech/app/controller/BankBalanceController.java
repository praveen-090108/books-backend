package com.intelliatech.app.controller;

import com.intelliatech.app.security.TenantContext;
import com.intelliatech.app.service.BankLedgerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bank-balances")
@RequiredArgsConstructor
public class BankBalanceController {
    private final JdbcTemplate jdbc;
    private final TenantContext tenants;
    private final BankLedgerService ledger;

    @GetMapping("/monthly-report")
    @PreAuthorize("@permissionGuard.can('reports','sales_reports','VIEW')")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Map<String,Object> monthlyReport(@RequestParam java.time.YearMonth month) {
        LocalDate start = month.atDay(1), end = month.atEndOfMonth();
        long organization = org();
        var entries = jdbc.queryForList("""
            SELECT a.account_name, a.active, e.transaction_date, e.transaction_type, e.source_type,
                   e.balance_category, e.direction, e.amount, e.currency_code, e.reference_number, e.description
            FROM bank_account_ledger_entries e
            JOIN bank_account_master a ON a.id=e.bank_account_id AND a.organization_id=e.organization_id
            WHERE e.organization_id=? AND e.transaction_date BETWEEN ? AND ?
              AND e.source_type IN ('PAYMENT_RECEIVED','OPENING_BALANCE','EXPENSE','BILL_PAYMENT')
            ORDER BY e.transaction_date,e.id
            """, organization, start, end);
        var balances = jdbc.queryForList("""
            SELECT a.account_name,a.currency_code,
              COALESCE(SUM(CASE WHEN e.balance_category='COUNTABLE' THEN IF(e.direction='IN',e.amount,-e.amount) ELSE 0 END),0) countable,
              COALESCE(SUM(CASE WHEN e.balance_category='NON_COUNTABLE' THEN IF(e.direction='IN',e.amount,-e.amount) ELSE 0 END),0) non_countable,
              COALESCE(SUM(CASE WHEN e.transaction_date>=? THEN IF(e.direction='IN',e.amount,-e.amount) ELSE 0 END),0) month_movement,
              COALESCE(SUM(IF(e.direction='IN',e.amount,-e.amount)),0) closing
            FROM bank_account_master a LEFT JOIN bank_account_ledger_entries e
              ON e.bank_account_id=a.id AND e.organization_id=a.organization_id AND e.transaction_date<=?
            WHERE a.organization_id=? AND a.active=TRUE
            GROUP BY a.id,a.account_name,a.currency_code ORDER BY a.account_name,a.id
            """, start, end, organization);
        return Map.of("entries", entries, "balances", balances);
    }

    public record OpeningRequest(@NotNull LocalDate openingDate, @DecimalMin("0.00") BigDecimal countableAmount,
                                 @DecimalMin("0.00") BigDecimal nonCountableAmount, String remarks) {}
    public record TransferRequest(@NotNull Long fromBankAccountId, @NotBlank String fromCategory,
                                  @NotNull Long toBankAccountId, @NotBlank String toCategory,
                                  @NotNull @DecimalMin("0.01") BigDecimal amount, @NotNull LocalDate transferDate,
                                  String referenceNumber, String remarks, String idempotencyKey) {}
    public record ReversalRequest(@NotBlank String reason) {}

    @GetMapping("/overview")
    @PreAuthorize("@permissionGuard.can('settings','bankAccounts','VIEW')")
    public Map<String,Object> overview(@RequestParam(required=false) LocalDate asOf) {
        List<Map<String,Object>> accounts = jdbc.queryForList("""
                SELECT a.id,a.account_name,a.bank_name,a.account_number,a.currency_code,a.active,
                  COALESCE(SUM(CASE WHEN e.balance_category='COUNTABLE' AND e.direction='IN' THEN e.amount WHEN e.balance_category='COUNTABLE' THEN -e.amount ELSE 0 END),0) countable_balance,
                  COALESCE(SUM(CASE WHEN e.balance_category='NON_COUNTABLE' AND e.direction='IN' THEN e.amount WHEN e.balance_category='NON_COUNTABLE' THEN -e.amount ELSE 0 END),0) non_countable_balance,
                  COALESCE(SUM(CASE WHEN e.direction='IN' THEN e.amount ELSE -e.amount END),0) total_balance,
                  MAX(e.transaction_date) last_transaction
                FROM bank_account_master a LEFT JOIN bank_account_ledger_entries e ON e.bank_account_id=a.id AND e.organization_id=a.organization_id
                  AND (? IS NULL OR e.transaction_date<=?)
                WHERE a.organization_id=? GROUP BY a.id,a.account_name,a.bank_name,a.account_number,a.currency_code,a.active ORDER BY a.account_name
                """, asOf, asOf, org());
        Map<String,Map<String,BigDecimal>> currencyTotals = new LinkedHashMap<>();
        for (Map<String,Object> account : accounts) {
            account.put("masked_account_number", mask((String)account.remove("account_number")));
            String currency=String.valueOf(account.get("currency_code"));
            Map<String,BigDecimal> totals=currencyTotals.computeIfAbsent(currency,k->new LinkedHashMap<>(Map.of("countable",BigDecimal.ZERO,"nonCountable",BigDecimal.ZERO,"total",BigDecimal.ZERO)));
            totals.put("countable",totals.get("countable").add((BigDecimal)account.get("countable_balance")));
            totals.put("nonCountable",totals.get("nonCountable").add((BigDecimal)account.get("non_countable_balance")));
            totals.put("total",totals.get("total").add((BigDecimal)account.get("total_balance")));
        }
        Map<String,Object> result=new LinkedHashMap<>(); result.put("asOf",asOf==null?LocalDate.now():asOf); result.put("currencyTotals",currencyTotals); result.put("accounts",accounts);
        result.put("activityTotals",activityTotals(asOf));
        result.put("unclassified",unclassified()); return result;
    }

    @GetMapping("/accounts/{accountId}")
    @PreAuthorize("@permissionGuard.can('settings','bankAccounts','VIEW')")
    public Map<String,Object> account(@PathVariable Long accountId, @RequestParam(required=false) LocalDate fromDate,
                                      @RequestParam(required=false) LocalDate toDate) {
        Map<String,Object> account=jdbc.queryForMap("SELECT id,account_name,bank_name,account_holder_name,account_number,account_type,ifsc_code,branch_name,currency_code,active FROM bank_account_master WHERE organization_id=? AND id=?",org(),accountId);
        account.put("maskedAccountNumber",mask((String)account.remove("account_number")));
        account.put("countableBalance",ledger.balance(accountId,BankLedgerService.COUNTABLE,toDate));
        account.put("nonCountableBalance",ledger.balance(accountId,BankLedgerService.NON_COUNTABLE,toDate));
        account.put("transactions",transactions(accountId,fromDate,toDate));
        return account;
    }

    @GetMapping("/accounts/{accountId}/transactions")
    @PreAuthorize("@permissionGuard.can('settings','bankAccounts','VIEW')")
    public List<Map<String,Object>> transactions(@PathVariable Long accountId,@RequestParam(required=false) LocalDate fromDate,@RequestParam(required=false) LocalDate toDate){
        return jdbc.queryForList("""
          SELECT id,balance_category,transaction_type,direction,source_type,source_id,transaction_date,amount,
                 currency_code,reference_number,description,status,created_by,created_at,
                 running_balance,category_running_balance
          FROM (
            SELECT e.*,
              SUM(CASE WHEN direction='IN' THEN amount ELSE -amount END)
                OVER (ORDER BY transaction_date,id) running_balance,
              SUM(CASE WHEN direction='IN' THEN amount ELSE -amount END)
                OVER (PARTITION BY balance_category ORDER BY transaction_date,id) category_running_balance
            FROM bank_account_ledger_entries e
            WHERE organization_id=? AND bank_account_id=? AND (? IS NULL OR transaction_date<=?)
          ) ledger
          WHERE (? IS NULL OR transaction_date>=?)
          ORDER BY transaction_date,id
          """,org(),accountId,toDate,toDate,fromDate,fromDate);
    }

    @PostMapping("/accounts/{accountId}/opening-balance")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionGuard.can('settings','bankAccounts','EDIT')")
    public void opening(@PathVariable Long accountId,@Valid @RequestBody OpeningRequest request){ledger.initializeOpening(accountId,request.openingDate(),request.countableAmount(),request.nonCountableAmount(),request.remarks());}

    @GetMapping("/transfers")
    @PreAuthorize("@permissionGuard.can('settings','bankAccounts','VIEW')")
    public List<Map<String,Object>> transfers(){return jdbc.queryForList("""
      SELECT t.id,t.transfer_number,t.transfer_date,fa.account_name from_account,t.from_category,ta.account_name to_account,t.to_category,t.amount,t.currency_code,t.reference_number,t.remarks,t.status,t.created_by,t.created_at,t.reversal_reason
      FROM bank_internal_transfers t JOIN bank_account_master fa ON fa.id=t.from_bank_account_id JOIN bank_account_master ta ON ta.id=t.to_bank_account_id
      WHERE t.organization_id=? ORDER BY t.transfer_date DESC,t.id DESC
      """,org());}

    @PostMapping("/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionGuard.can('settings','bankAccounts','CREATE')")
    public Map<String,Object> transfer(@Valid @RequestBody TransferRequest request){return ledger.transfer(request.fromBankAccountId(),request.fromCategory(),request.toBankAccountId(),request.toCategory(),request.amount(),request.transferDate(),request.referenceNumber(),request.remarks(),request.idempotencyKey()==null? UUID.randomUUID().toString():request.idempotencyKey());}

    @PostMapping("/transfers/{id}/reverse")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@permissionGuard.can('settings','bankAccounts','EDIT')")
    public void reverse(@PathVariable Long id,@Valid @RequestBody ReversalRequest request){ledger.reverseTransfer(id,request.reason());}

    @GetMapping("/reconciliation")
    @PreAuthorize("@permissionGuard.can('settings','bankAccounts','VIEW')")
    public Map<String,Object> reconciliation(){return unclassified();}

    private Map<String,Object> unclassified(){
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("invoicePayments",jdbc.queryForObject("SELECT COUNT(*) FROM invoice_payments WHERE organization_id=? AND bank_account_id IS NOT NULL AND balance_category IS NULL AND reversed=FALSE",Long.class,org()));
        result.put("expenses",jdbc.queryForObject("SELECT COUNT(*) FROM expenses WHERE organization_id=? AND bank_account_id IS NOT NULL AND balance_category IS NULL AND deleted=FALSE",Long.class,org()));
        result.put("billPayments",jdbc.queryForObject("SELECT COUNT(*) FROM bill_payments bp JOIN bills b ON b.id=bp.bill_id WHERE b.organization_id=? AND bp.bank_account_id IS NOT NULL AND bp.balance_category IS NULL AND bp.status<>'REVERSED'",Long.class,org()));
        return result;
    }
    private Map<String,Map<String,BigDecimal>> activityTotals(LocalDate asOf) {
        List<Map<String,Object>> entries = jdbc.queryForList("""
          SELECT currency_code,
            COALESCE(SUM(CASE WHEN direction='IN' AND transaction_type<>'OPENING_BALANCE' AND source_type<>'INTERNAL_TRANSFER' THEN amount ELSE 0 END),0) money_received,
            COALESCE(SUM(CASE WHEN direction='OUT' AND source_type<>'INTERNAL_TRANSFER' THEN amount ELSE 0 END),0) money_spent
          FROM bank_account_ledger_entries
          WHERE organization_id=? AND (? IS NULL OR transaction_date<=?)
          GROUP BY currency_code
          """, org(), asOf, asOf);
        List<Map<String,Object>> transfers = jdbc.queryForList("""
          SELECT currency_code,COALESCE(SUM(amount),0) internal_transfers
          FROM bank_internal_transfers
          WHERE organization_id=? AND status='POSTED' AND (? IS NULL OR transfer_date<=?)
          GROUP BY currency_code
          """, org(), asOf, asOf);
        Map<String,Map<String,BigDecimal>> totals = new LinkedHashMap<>();
        for (Map<String,Object> row : entries) {
            totals.put(String.valueOf(row.get("currency_code")), new LinkedHashMap<>(Map.of(
                    "moneyReceived", (BigDecimal) row.get("money_received"),
                    "moneySpent", (BigDecimal) row.get("money_spent"),
                    "internalTransfers", BigDecimal.ZERO)));
        }
        for (Map<String,Object> row : transfers) {
            totals.computeIfAbsent(String.valueOf(row.get("currency_code")), ignored -> new LinkedHashMap<>(Map.of(
                    "moneyReceived", BigDecimal.ZERO, "moneySpent", BigDecimal.ZERO, "internalTransfers", BigDecimal.ZERO)))
                    .put("internalTransfers", (BigDecimal) row.get("internal_transfers"));
        }
        return totals;
    }
    private Long org(){return tenants.requireOrganizationId();}
    private String mask(String value){if(value==null||value.isBlank())return "-";String compact=value.replaceAll("\\s","");return "••••"+compact.substring(Math.max(0,compact.length()-4));}
}
