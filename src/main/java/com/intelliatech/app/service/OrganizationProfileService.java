package com.intelliatech.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.intelliatech.app.dto.request.OrganizationProfileRequest;
import com.intelliatech.app.dto.response.OrganizationProfileResponse;
import com.intelliatech.app.dto.response.OrganizationIdentityResponse;
import com.intelliatech.app.entity.Organization;
import com.intelliatech.app.exception.ResourceNotFoundException;
import com.intelliatech.app.repository.OrganizationRepository;
import com.intelliatech.app.security.TenantContext;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class OrganizationProfileService {
    private final OrganizationRepository organizations;
    private final TenantContext tenants;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public OrganizationProfileResponse get() {
        Long organizationId = tenants.requireOrganizationId();
        Organization organization = organizations.findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found."));
        return response(organization, settings(organizationId));
    }

    @Transactional(readOnly = true)
    public OrganizationIdentityResponse identity() {
        Long organizationId = tenants.requireOrganizationId();
        Organization organization = organizations.findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found."));
        String notes = jdbc.query("""
                SELECT notes FROM business_records
                WHERE organization_id=? AND module='settings' AND type='branding'
                ORDER BY record_date DESC,id DESC LIMIT 1
                """, rs -> rs.next() ? rs.getString("notes") : null, organizationId);
        return new OrganizationIdentityResponse(organizationId, organization.getOrganizationName(), logoUrl(notes));
    }

    @Transactional
    public OrganizationProfileResponse update(OrganizationProfileRequest request) {
        Long organizationId = tenants.requireOrganizationId();
        Organization organization = organizations.findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found."));
        organization.setOrganizationName(request.organizationName().trim());
        organization.setLegalName(clean(request.legalName()));
        organization.setGstin(upper(request.gstin()));
        organization.setPan(upper(request.pan()));
        organization.setCountryCode(request.countryCode().trim().toUpperCase(Locale.ROOT));
        organizations.save(organization);

        ObjectNode onboarding = onboarding(settings(organizationId).onboardingJson());
        if (StringUtils.hasText(request.tdsBaseType())) onboarding.put("tdsBaseType", request.tdsBaseType().trim());
        jdbc.update("""
                UPDATE organization_settings
                SET contact_email=?, phone=?, address_line1=?, address_line2=?, city=?, state_name=?, postal_code=?,
                    base_currency=?, onboarding_json=?
                WHERE organization_id=?
                """, clean(request.email()), clean(request.phone()), clean(request.addressLine1()),
                clean(request.addressLine2()), clean(request.city()), clean(request.state()), clean(request.postalCode()),
                currency(request.baseCurrency()), onboarding.toString(), organizationId);
        return response(organization, settings(organizationId));
    }

    private Settings settings(Long organizationId) {
        return jdbc.queryForObject("""
                SELECT contact_email,phone,address_line1,address_line2,city,state_name,postal_code,base_currency,onboarding_json
                FROM organization_settings WHERE organization_id=?
                """, (rs, row) -> new Settings(rs.getString("contact_email"), rs.getString("phone"),
                rs.getString("address_line1"), rs.getString("address_line2"), rs.getString("city"),
                rs.getString("state_name"), rs.getString("postal_code"), rs.getString("base_currency"),
                rs.getString("onboarding_json")), organizationId);
    }

    private OrganizationProfileResponse response(Organization organization, Settings settings) {
        String tdsBaseType = "TAXABLE_VALUE";
        try {
            JsonNode value = objectMapper.readTree(settings.onboardingJson());
            if (value != null && value.hasNonNull("tdsBaseType")) tdsBaseType = value.get("tdsBaseType").asText();
        } catch (Exception ignored) { }
        return new OrganizationProfileResponse(organization.getId(), organization.getOrganizationName(),
                organization.getLegalName(), organization.getGstin(), organization.getPan(), organization.getCountryCode(),
                settings.email(), settings.phone(), settings.addressLine1(), settings.addressLine2(), settings.city(),
                settings.state(), settings.postalCode(), settings.baseCurrency(), tdsBaseType);
    }

    private ObjectNode onboarding(String json) {
        try {
            JsonNode value = objectMapper.readTree(json);
            if (value instanceof ObjectNode object) return object;
        } catch (Exception ignored) { }
        return objectMapper.createObjectNode();
    }

    private String logoUrl(String notes) {
        if (!StringUtils.hasText(notes)) return null;
        try {
            String value = objectMapper.readTree(notes).path("logoUrl").asText("");
            return StringUtils.hasText(value) ? value : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String currency(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "INR";
    }
    private String upper(String value) { String cleaned = clean(value); return cleaned == null ? null : cleaned.toUpperCase(Locale.ROOT); }
    private String clean(String value) { return StringUtils.hasText(value) ? value.trim() : null; }

    private record Settings(String email, String phone, String addressLine1, String addressLine2, String city,
                            String state, String postalCode, String baseCurrency, String onboardingJson) {}
}
