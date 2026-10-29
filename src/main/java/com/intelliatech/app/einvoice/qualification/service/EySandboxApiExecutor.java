package com.intelliatech.app.einvoice.qualification.service;

import static com.intelliatech.app.einvoice.qualification.dto.QualificationModels.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.intelliatech.app.entity.IrpEnvironment;
import com.intelliatech.app.service.IrpAuthenticationService;
import com.intelliatech.app.service.IrpClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class EySandboxApiExecutor {
 private final IrpAuthenticationService authentication; private final IrpClient client;
 public Execution authenticate(){try{authentication.refreshToken(IrpEnvironment.SANDBOX);return new Execution(true,200,null,"Authentication succeeded",null);}catch(Exception e){return new Execution(false,0,"AUTHENTICATION_FAILED",safe(e),null);}}
 public Execution generate(JsonNode fixture){return from(client.generateIrn(fixture));}
 public Execution get(String irn){return from(client.getIrn(irn,IrpEnvironment.SANDBOX));}
 public Execution cancel(String irn){return from(client.cancelIrn(irn,"1","Qualification sandbox test",IrpEnvironment.SANDBOX));}
 private Execution from(com.intelliatech.app.dto.response.IrpGenerateResult r){return new Execution(r.success(),r.httpStatus(),r.errorCode(),r.errorMessage(),r.decryptedData());}
 private String safe(Exception e){String m=e.getMessage();return m==null?e.getClass().getSimpleName():m.replaceAll("(?i)(password|secret|token)\\s*[:=]\\s*[^,; ]+","$1=[REDACTED]");}
}
