package com.intelliatech.app.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intelliatech.app.dto.response.IrpAuthenticationToken;
import com.intelliatech.app.dto.response.IrpCredentials;
import com.intelliatech.app.dto.response.IrpGenerateResult;
import com.intelliatech.app.entity.IrpEnvironment;
import com.intelliatech.app.service.IrpAuthenticationService;
import com.intelliatech.app.service.IrpClient;
import com.intelliatech.app.service.IrpConfigurationService;
import com.intelliatech.app.service.IrpCryptoService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriUtils;
import java.nio.charset.StandardCharsets;

@Service
public class EyIrpClient implements IrpClient {

    private static final Long COMPANY_ID = 1L;
    private final IrpConfigurationService configurations;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final IrpAuthenticationService authenticationService;
    private final IrpCryptoService cryptoService;

    public EyIrpClient(
            IrpConfigurationService configurations,
            @Qualifier("irpRestClient") RestClient restClient,
            ObjectMapper objectMapper,
            IrpAuthenticationService authenticationService,
            IrpCryptoService cryptoService
    ) {
        this.configurations = configurations;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.authenticationService = authenticationService;
        this.cryptoService = cryptoService;
    }

    @Override
    public IrpGenerateResult generateIrn(JsonNode invoicePayload) {
        IrpCredentials credentials = configurations.getActiveCredentials(COMPANY_ID);
        IrpAuthenticationToken token = authenticationService.currentToken(credentials.environment());
        try {
            String plainRequest = objectMapper.writeValueAsString(invoicePayload);
            String encrypted = cryptoService.encryptPayload(plainRequest, token.sessionEncryptionKey());
            JsonNode response = restClient.post()
                    .uri(credentials.generateUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header("client_id", credentials.clientId())
                    .header("client_secret", credentials.clientSecret())
                    .header("Gstin", credentials.gstin())
                    .header("user_name", token.username())
                    .header("AuthToken", token.authToken())
                    .body(encryptedBody(encrypted))
                    .retrieve()
                    .body(JsonNode.class);
            return parseResponse(response, token, 200);
        } catch (RestClientResponseException exception) {
            JsonNode response = parseBody(exception.getResponseBodyAsString());
            return parseResponse(response, token, exception.getStatusCode().value());
        } catch (Exception exception) {
            return new IrpGenerateResult(false, 0, null, null, "IRP_TRANSPORT_ERROR", safeMessage(exception));
        }
    }

    @Override
    public IrpGenerateResult cancelIrn(String irn, String reasonCode, String remarks) {
        return cancelIrn(irn, reasonCode, remarks, configurations.getActiveConfiguration(COMPANY_ID).getEnvironment());
    }

    @Override
    public IrpGenerateResult cancelIrn(String irn, String reasonCode, String remarks, IrpEnvironment environment) {
        IrpCredentials credentials = configurations.getDecryptedCredentials(COMPANY_ID, environment);
        IrpAuthenticationToken token = authenticationService.currentToken(environment);
        try {
            JsonNode payload = objectMapper.valueToTree(Map.of(
                    "Irn", irn,
                    "CnlRsn", reasonCode,
                    "CnlRem", remarks == null ? "" : remarks
            ));
            String encrypted = cryptoService.encryptPayload(objectMapper.writeValueAsString(payload), token.sessionEncryptionKey());
            JsonNode response = restClient.post()
                    .uri(credentials.cancelUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(headers -> applyAuthenticatedHeaders(headers, token, credentials))
                    .body(encryptedBody(encrypted))
                    .retrieve().body(JsonNode.class);
            return parseResponse(response, token, 200);
        } catch (RestClientResponseException exception) {
            return parseResponse(parseBody(exception.getResponseBodyAsString()), token, exception.getStatusCode().value());
        } catch (Exception exception) {
            return new IrpGenerateResult(false, 0, null, null, "IRP_TRANSPORT_ERROR", safeMessage(exception));
        }
    }

    @Override
    public IrpGenerateResult getIrn(String irn) {
        return getIrn(irn, configurations.getActiveConfiguration(COMPANY_ID).getEnvironment());
    }

    @Override
    public IrpGenerateResult getIrn(String irn, IrpEnvironment environment) {
        IrpCredentials credentials = configurations.getDecryptedCredentials(COMPANY_ID, environment);
        IrpAuthenticationToken token = authenticationService.currentToken(environment);
        try {
            String url = credentials.searchUrl() + "/" + UriUtils.encodePathSegment(irn, StandardCharsets.UTF_8);
            JsonNode response = restClient.get()
                    .uri(url)
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(headers -> applyAuthenticatedHeaders(headers, token, credentials))
                    .retrieve().body(JsonNode.class);
            return parseResponse(response, token, 200);
        } catch (RestClientResponseException exception) {
            return parseResponse(parseBody(exception.getResponseBodyAsString()), token, exception.getStatusCode().value());
        } catch (Exception exception) {
            return new IrpGenerateResult(false, 0, null, null, "IRP_TRANSPORT_ERROR", safeMessage(exception));
        }
    }

    private void applyAuthenticatedHeaders(org.springframework.http.HttpHeaders headers, IrpAuthenticationToken token, IrpCredentials credentials) {
        headers.set("client_id", credentials.clientId());
        headers.set("client_secret", credentials.clientSecret());
        headers.set("Gstin", credentials.gstin());
        headers.set("user_name", token.username());
        headers.set("AuthToken", token.authToken());
    }

    private IrpGenerateResult parseResponse(JsonNode response, IrpAuthenticationToken token, int httpStatus) {
        if (response != null && response.path("Status").asInt(response.path("status").asInt()) == 1) {
            try {
                String encryptedData = response.path("Data").asText(response.path("data").asText());
                if (!StringUtils.hasText(encryptedData)) {
                    return new IrpGenerateResult(false, httpStatus, response, null,
                            "INVALID_IRP_RESPONSE", "IRP returned success without encrypted Data");
                }
                JsonNode decrypted = objectMapper.readTree(
                        cryptoService.decryptPayload(encryptedData, token.sessionEncryptionKey())
                );
                return new IrpGenerateResult(true, httpStatus, response, decrypted, null, null);
            } catch (Exception exception) {
                return new IrpGenerateResult(false, httpStatus, response, null,
                        "INVALID_IRP_RESPONSE", safeMessage(exception));
            }
        }
        List<String> codes = new ArrayList<>();
        List<String> messages = new ArrayList<>();
        JsonNode errors = response == null ? null : response.path("ErrorDetails");
        if (errors != null && errors.isArray()) {
            errors.forEach(error -> {
                addText(codes, error, "ErrorCode");
                addText(messages, error, "ErrorMessage");
            });
        } else if (errors != null && !errors.isMissingNode() && !errors.isNull()) {
            messages.add(errors.isTextual() ? errors.asText() : errors.toString());
        }
        if (response != null && StringUtils.hasText(response.path("InfoDtls").asText())) {
            messages.add(response.path("InfoDtls").asText());
        }
        if (response != null && StringUtils.hasText(response.path("Error").asText())) {
            messages.add(response.path("Error").asText());
        }
        return new IrpGenerateResult(false, httpStatus, response, null,
                codes.isEmpty() ? "IRP_REJECTED" : String.join(",", codes),
                messages.isEmpty() ? "IRP rejected the Generate IRN request" : String.join("; ", messages));
    }

    private JsonNode parseBody(String body) {
        try {
            return StringUtils.hasText(body) ? objectMapper.readTree(body) : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String encryptedBody(String encrypted) throws Exception {
        return objectMapper.writeValueAsString(Map.of("Data", encrypted));
    }

    private void addText(List<String> values, JsonNode node, String field) {
        String value = node.path(field).asText();
        if (StringUtils.hasText(value)) values.add(value);
    }

    private String safeMessage(Exception exception) {
        return StringUtils.hasText(exception.getMessage()) ? exception.getMessage() : exception.getClass().getSimpleName();
    }
}
