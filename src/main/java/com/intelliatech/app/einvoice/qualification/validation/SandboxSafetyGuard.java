package com.intelliatech.app.einvoice.qualification.validation;

import com.intelliatech.app.einvoice.qualification.config.QualificationProperties;
import com.intelliatech.app.entity.IrpEnvironment;
import com.intelliatech.app.service.IrpConfigurationService;
import java.net.URI;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class SandboxSafetyGuard {
 private final QualificationProperties properties; private final IrpConfigurationService configurations; private final Environment environment;
 public void verify(){
  if(!properties.isEnabled()) throw new IllegalStateException("Qualification mode is disabled");
  if(!properties.isExecutionPermitted()) throw new IllegalStateException("Qualification external execution is not explicitly permitted");
  String appEnvironment=environment.getProperty("app.environment","");
  if(!"sandbox".equalsIgnoreCase(appEnvironment)) throw new IllegalStateException("Application environment is not SANDBOX");
  var configuration=configurations.getActiveConfiguration(1L);
  if(configuration.getEnvironment()!=IrpEnvironment.SANDBOX) throw new IllegalStateException("Active IRP configuration is not SANDBOX");
  String host=URI.create(configuration.getApiBaseUrl()).getHost();
  if(host==null) throw new IllegalStateException("Sandbox endpoint host is ambiguous");
  String normalized=host.toLowerCase(Locale.ROOT);
  boolean allowed=properties.getAllowedSandboxHosts().stream().map(v->v.toLowerCase(Locale.ROOT)).anyMatch(normalized::equals);
  if(!allowed) throw new IllegalStateException("IRP host is not in the qualification sandbox allowlist");
  if(normalized.contains("prod")||normalized.contains("production")) throw new IllegalStateException("Production IRP endpoints are prohibited");
  configurations.getDecryptedCredentials(1L,IrpEnvironment.SANDBOX);
 }
}
