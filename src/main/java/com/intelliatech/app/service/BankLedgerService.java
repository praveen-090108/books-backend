package com.intelliatech.app.service;

import com.intelliatech.app.entity.BankAccountMaster;
import com.intelliatech.app.exception.ResourceConflictException;
import com.intelliatech.app.exception.ResourceNotFoundException;
import com.intelliatech.app.repository.BankAccountMasterRepository;
import com.intelliatech.app.security.TenantContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BankLedgerService {
    public static final String COUNTABLE = "COUNTABLE";
    public static final String NON_COUNTABLE = "NON_COUNTABLE";

    private final JdbcTemplate jdbc;
    private final TenantContext tenants;
    private final BankAccountMasterRepository accounts;

    public record Posting(Long bankAccountId, String category, String transactionType, String sourceType,
                          Long sourceId, String sourceKey, LocalDate transactionDate, BigDecimal amount,
                          String direction, String currency, String reference, String description) {}

    @Transactional
    public void sync(Posting posting) {
        if (posting.bankAccountId() == null || posting.amount() == null || posting.amount().signum() <= 0) {
            reverse(posting.sourceType(), posting.sourceKey(), "Source transaction no longer affects a bank account.");
            return;
        }
        BankAccountMaster account = accountForUpdate(posting.bankAccountId());
        String category = category(posting.category());
        String direction = direction(posting.direction());
        BigDecimal amount = money(posting.amount());
        if (!account.getCurrencyCode().equalsIgnoreCase(posting.currency())) {
            throw new IllegalArgumentException("The transaction currency must match the selected Bank Account currency.");
        }
        List<Map<String,Object>> current = activeSource(posting.sourceType(), posting.sourceKey());
        if (current.size() == 1 && same(current.get(0), posting, category, direction, amount)) return;
        reverseRows(current, "Source transaction updated.");
        insert(account.getId(), category, posting.transactionType(), direction, posting.sourceType(), posting.sourceId(),
                posting.sourceKey(), posting.transactionDate(), amount, account.getCurrencyCode(), posting.reference(),
                posting.description(), null);
    }

    @Transactional
    public void reverse(String sourceType, String sourceKey, String reason) {
        reverseRows(activeSource(sourceType, sourceKey), reason);
    }

    @Transactional
    public void initializeOpening(Long accountId, LocalDate date, BigDecimal countable, BigDecimal nonCountable, String remarks) {
        BankAccountMaster account = accountForUpdate(accountId);
        BigDecimal c = nonNegative(countable);
        BigDecimal n = nonNegative(nonCountable);
        if (c.signum() == 0 && n.signum() == 0) throw new IllegalArgumentException("Enter a Countable or Non-Countable opening balance.");
        jdbc.update("INSERT INTO bank_opening_balance_initializations(organization_id,bank_account_id,opening_date,countable_amount,non_countable_amount,remarks,created_by) VALUES (?,?,?,?,?,?,?)",
                org(), accountId, date, c, n, clean(remarks), user());
        Long additionId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        String sourceKey = "OPENING_ADDITION:" + additionId;
        if (c.signum() > 0) insert(accountId, COUNTABLE, "OPENING_BALANCE", "IN", "OPENING_BALANCE", additionId,
                sourceKey + ":COUNTABLE", date, c, account.getCurrencyCode(), null, remarks, null);
        if (n.signum() > 0) insert(accountId, NON_COUNTABLE, "OPENING_BALANCE", "IN", "OPENING_BALANCE", additionId,
                sourceKey + ":NON_COUNTABLE", date, n, account.getCurrencyCode(), null, remarks, null);
    }

    @Transactional
    public Map<String,Object> transfer(Long fromAccountId, String fromCategory, Long toAccountId, String toCategory,
                                       BigDecimal amount, LocalDate date, String reference, String remarks,
                                       String idempotencyKey) {
        if (fromAccountId.equals(toAccountId) && category(fromCategory).equals(category(toCategory))) {
            throw new IllegalArgumentException("Source and destination account/category cannot be identical.");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) throw new IllegalArgumentException("Transfer request key is required.");
        List<Map<String,Object>> duplicate = jdbc.queryForList("SELECT * FROM bank_internal_transfers WHERE organization_id=? AND idempotency_key=?", org(), idempotencyKey.trim());
        if (!duplicate.isEmpty()) return duplicate.get(0);
        long first = Math.min(fromAccountId, toAccountId), second = Math.max(fromAccountId, toAccountId);
        BankAccountMaster firstAccount = accountForUpdate(first);
        BankAccountMaster secondAccount = first == second ? firstAccount : accountForUpdate(second);
        BankAccountMaster from = fromAccountId == first ? firstAccount : secondAccount;
        BankAccountMaster to = toAccountId == first ? firstAccount : secondAccount;
        if (!from.getCurrencyCode().equalsIgnoreCase(to.getCurrencyCode())) {
            throw new IllegalArgumentException("Cross-currency transfers are not supported without an exchange-rate workflow.");
        }
        BigDecimal value = positive(amount);
        String fromType = category(fromCategory), toType = category(toCategory);
        if (balance(fromAccountId, fromType, null).compareTo(value) < 0) throw new ResourceConflictException("Insufficient source balance for this transfer.");
        String number = "TRF-%06d".formatted(nextTransferSequence());
        jdbc.update("INSERT INTO bank_internal_transfers(organization_id,transfer_number,idempotency_key,from_bank_account_id,from_category,to_bank_account_id,to_category,amount,currency_code,transfer_date,reference_number,remarks,status,created_by,posted_by,posted_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?, 'POSTED',?,?,?)",
                org(), number, idempotencyKey.trim(), fromAccountId, fromType, toAccountId, toType, value,
                from.getCurrencyCode(), date, clean(reference), clean(remarks), user(), user(), LocalDateTime.now());
        Long transferId = jdbc.queryForObject("SELECT id FROM bank_internal_transfers WHERE organization_id=? AND transfer_number=?", Long.class, org(), number);
        insert(fromAccountId, fromType, "TRANSFER_OUT", "OUT", "INTERNAL_TRANSFER", transferId, number + ":OUT", date, value, from.getCurrencyCode(), reference, remarks, null);
        insert(toAccountId, toType, "TRANSFER_IN", "IN", "INTERNAL_TRANSFER", transferId, number + ":IN", date, value, to.getCurrencyCode(), reference, remarks, null);
        return jdbc.queryForMap("SELECT * FROM bank_internal_transfers WHERE organization_id=? AND id=?", org(), transferId);
    }

    @Transactional
    public void reverseTransfer(Long transferId, String reason) {
        Map<String,Object> transfer = jdbc.queryForMap("SELECT * FROM bank_internal_transfers WHERE organization_id=? AND id=? FOR UPDATE", org(), transferId);
        if ("REVERSED".equals(transfer.get("status"))) throw new ResourceConflictException("This transfer is already reversed.");
        String number = String.valueOf(transfer.get("transfer_number"));
        reverse("INTERNAL_TRANSFER", number + ":OUT", reason);
        reverse("INTERNAL_TRANSFER", number + ":IN", reason);
        jdbc.update("UPDATE bank_internal_transfers SET status='REVERSED',reversal_reason=?,reversed_by=?,reversed_at=? WHERE organization_id=? AND id=?",
                clean(reason), user(), LocalDateTime.now(), org(), transferId);
    }

    public BigDecimal balance(Long accountId, String category, LocalDate asOf) {
        String sql = "SELECT COALESCE(SUM(CASE WHEN direction='IN' THEN amount ELSE -amount END),0) FROM bank_account_ledger_entries WHERE organization_id=? AND bank_account_id=? AND balance_category=?";
        if (asOf == null) return money(jdbc.queryForObject(sql, BigDecimal.class, org(), accountId, category(category)));
        return money(jdbc.queryForObject(sql + " AND transaction_date<=?", BigDecimal.class, org(), accountId, category(category), asOf));
    }

    private BankAccountMaster accountForUpdate(Long id) {
        jdbc.queryForObject("SELECT id FROM bank_account_master WHERE organization_id=? AND id=? FOR UPDATE", Long.class, org(), id);
        return accounts.findByIdAndOrganizationId(id, org()).orElseThrow(() -> new ResourceNotFoundException("Bank Account not found."));
    }
    private List<Map<String,Object>> activeSource(String type, String key) { return jdbc.queryForList("SELECT * FROM bank_account_ledger_entries WHERE organization_id=? AND source_type=? AND source_key=? AND status='POSTED' ORDER BY id", org(), type, key); }
    private void reverseRows(List<Map<String,Object>> rows, String reason) {
        for (Map<String,Object> row : rows) {
            Long id = ((Number) row.get("id")).longValue();
            jdbc.update("UPDATE bank_account_ledger_entries SET status='REVERSED' WHERE organization_id=? AND id=? AND status='POSTED'", org(), id);
            insert(((Number)row.get("bank_account_id")).longValue(), String.valueOf(row.get("balance_category")), "REVERSAL",
                    "IN".equals(row.get("direction")) ? "OUT" : "IN", String.valueOf(row.get("source_type")),
                    row.get("source_id") == null ? null : ((Number)row.get("source_id")).longValue(), String.valueOf(row.get("source_key")) + ":REV:" + id,
                    LocalDate.now(), (BigDecimal)row.get("amount"), String.valueOf(row.get("currency_code")),
                    String.valueOf(row.get("reference_number")), reason, id);
        }
    }
    private void insert(Long accountId,String category,String transactionType,String direction,String sourceType,Long sourceId,String sourceKey,LocalDate date,BigDecimal amount,String currency,String reference,String description,Long reversalOf) {
        jdbc.update("INSERT INTO bank_account_ledger_entries(organization_id,bank_account_id,balance_category,transaction_type,direction,source_type,source_id,source_key,transaction_date,amount,currency_code,reference_number,description,status,reversal_of_entry_id,created_by) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,'POSTED',?,?)",
                org(),accountId,category,transactionType,direction,sourceType,sourceId,sourceKey,date,money(amount),currency,clean(reference),clean(description),reversalOf,user());
    }
    private boolean same(Map<String,Object> row, Posting p, String category, String direction, BigDecimal amount) {
        return Objects.equals(((Number)row.get("bank_account_id")).longValue(), p.bankAccountId()) && category.equals(row.get("balance_category"))
                && direction.equals(row.get("direction")) && amount.compareTo((BigDecimal)row.get("amount"))==0
                && p.transactionDate().equals(((Date)row.get("transaction_date")).toLocalDate())
                && Objects.equals(clean(p.currency()), row.get("currency_code"));
    }
    private long nextTransferSequence() { Long value=jdbc.queryForObject("SELECT COALESCE(MAX(id),0)+1 FROM bank_internal_transfers WHERE organization_id=?",Long.class,org()); return value==null?1:value; }
    private String category(String value) { String normalized=value==null?"":value.trim().toUpperCase(); if(!COUNTABLE.equals(normalized)&&!NON_COUNTABLE.equals(normalized)) throw new IllegalArgumentException("Balance Type must be Countable or Non-Countable."); return normalized; }
    private String direction(String value) { String normalized=value==null?"":value.trim().toUpperCase(); if(!"IN".equals(normalized)&&!"OUT".equals(normalized)) throw new IllegalArgumentException("Bank ledger direction is invalid."); return normalized; }
    private BigDecimal positive(BigDecimal value){BigDecimal result=money(value);if(result.signum()<=0)throw new IllegalArgumentException("Amount must be greater than zero.");return result;}
    private BigDecimal nonNegative(BigDecimal value){BigDecimal result=money(value);if(result.signum()<0)throw new IllegalArgumentException("Opening balances cannot be negative.");return result;}
    private BigDecimal money(BigDecimal value){return (value==null?BigDecimal.ZERO:value).setScale(2,RoundingMode.HALF_UP);}
    private Long org(){return tenants.requireOrganizationId();}
    private String user(){return SecurityContextHolder.getContext().getAuthentication().getName();}
    private String clean(String value){return value==null||value.isBlank()?null:value.trim();}
}
