package com.intelliatech.app.controller;

import com.intelliatech.app.dto.response.CurrencyOptionResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/masters/currencies")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class CurrencyMasterController {
    private final JdbcTemplate jdbc;

    @GetMapping
    public List<CurrencyOptionResponse> list() {
        return jdbc.query(
                "SELECT code,name,symbol,decimal_places FROM currency_master WHERE active=TRUE ORDER BY display_order,code",
                (result, index) -> new CurrencyOptionResponse(
                        result.getString("code"), result.getString("name"), result.getString("symbol"),
                        result.getInt("decimal_places")));
    }
}
