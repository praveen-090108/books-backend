package com.intelliatech.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.intelliatech.app.repository.BankAccountMasterRepository;
import com.intelliatech.app.security.TenantContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class BankLedgerServiceTest {
    private JdbcTemplate jdbc;
    private BankLedgerService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        TenantContext tenants = mock(TenantContext.class);
        when(tenants.requireOrganizationId()).thenReturn(42L);
        service = new BankLedgerService(jdbc, tenants, mock(BankAccountMasterRepository.class));
    }

    @Test
    void returnsTenantScopedBalanceRoundedToMoneyPrecision() {
        when(jdbc.queryForObject(anyString(), eq(BigDecimal.class), any(), any(), any(), any()))
                .thenReturn(new BigDecimal("1250.555"));

        BigDecimal balance = service.balance(9L, BankLedgerService.COUNTABLE, LocalDate.of(2026, 11, 23));

        assertEquals(new BigDecimal("1250.56"), balance);
    }

    @Test
    void rejectsTransferWithoutARealSourceDestinationChange() {
        assertThrows(IllegalArgumentException.class, () -> service.transfer(
                9L, BankLedgerService.COUNTABLE,
                9L, BankLedgerService.COUNTABLE,
                BigDecimal.TEN, LocalDate.now(), null, null, "request-1"));
    }

    @Test
    void rejectsUnknownBalanceCategory() {
        assertThrows(IllegalArgumentException.class,
                () -> service.balance(9L, "PERSONAL", null));
    }
}
