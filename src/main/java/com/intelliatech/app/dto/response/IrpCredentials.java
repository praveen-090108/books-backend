package com.intelliatech.app.dto.response;

import com.intelliatech.app.entity.IrpEnvironment;

public record IrpCredentials(Long configurationId,Long companyId,String provider,IrpEnvironment environment,
 String apiBaseUrl,String clientId,String clientSecret,String apiUsername,String apiPassword,String gstin,
 String apiVersion,Long credentialVersion) {
    public String authUrl(){ return endpoint("/irpauthapi/v1.0/apiAuth"); }
    public String generateUrl(){ return endpoint("/irpapi/v1.0/api/generate"); }
    public String cancelUrl(){ return endpoint("/irpapi/v1.0/api/cancel"); }
    public String searchUrl(){ return endpoint("/irpapi/v1.0/enSearch"); }
    private String endpoint(String suffix){ return apiBaseUrl.replaceAll("/+$","")+suffix; }
}
