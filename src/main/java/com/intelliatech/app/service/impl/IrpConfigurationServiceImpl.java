package com.intelliatech.app.service.impl;

import com.intelliatech.app.dto.request.IrpConfigurationRequest;
import com.intelliatech.app.dto.response.*;
import com.intelliatech.app.entity.*;
import com.intelliatech.app.repository.*;
import com.intelliatech.app.service.*;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service @RequiredArgsConstructor
public class IrpConfigurationServiceImpl implements IrpConfigurationService {
 private static final String PROVIDER="EY_IRP_5";
 private final IrpConfigurationRepository repository;
 private final IrpConfigurationAuditRepository audits;
 private final CredentialEncryptionService encryption;
 private final ApplicationEventPublisher events;

 @Override @Transactional(readOnly=true) public List<IrpConfigurationResponse> list(Long companyId){return repository.findAllByCompanyIdOrderByEnvironment(companyId).stream().map(this::response).toList();}
 @Override @Transactional(readOnly=true) public IrpConfigurationResponse get(Long companyId,IrpEnvironment environment){return response(getConfiguration(companyId,environment));}
 @Override @Transactional public IrpConfigurationResponse save(Long companyId,IrpEnvironment environment,IrpConfigurationRequest r){
   validateUrl(r.apiBaseUrl(),environment);
   IrpConfiguration c=repository.findByCompanyIdAndProviderAndEnvironment(companyId,PROVIDER,environment).orElseGet(()->{
     var n=new IrpConfiguration(); n.setCompanyId(companyId); n.setProvider(PROVIDER); n.setEnvironment(environment); n.setCreatedBy(user()); return n;});
   boolean existing=c.getId()!=null;
   c.setApiBaseUrl(r.apiBaseUrl().trim()); c.setGstin(r.gstin().trim().toUpperCase(Locale.ROOT)); c.setApiVersion(clean(r.apiVersion())); c.setConfigured(true); c.setUpdatedBy(user());
   c.setClientIdEncrypted(secret(r.clientId(),c.getClientIdEncrypted(),"Client ID"));
   c.setClientSecretEncrypted(secret(r.clientSecret(),c.getClientSecretEncrypted(),"Client Secret"));
   c.setApiUsernameEncrypted(secret(r.apiUsername(),c.getApiUsernameEncrypted(),"API Username"));
   c.setApiPasswordEncrypted(secret(r.apiPassword(),c.getApiPasswordEncrypted(),"API Password"));
   if(existing) c.setCredentialVersion(c.getCredentialVersion()+1);
   c=repository.save(c); audit(c,existing?"UPDATED":"CREATED","IRP configuration saved; credentials were not logged.");
   events.publishEvent(new IrpConfigurationChangedEvent(companyId,environment)); return response(c);
 }
 @Override @Transactional public IrpConfigurationResponse activate(Long companyId,IrpEnvironment environment){
   List<IrpConfiguration> configs=repository.lockCompanyConfigurations(companyId);
   IrpConfiguration selected=configs.stream().filter(c->c.getEnvironment()==environment&&PROVIDER.equals(c.getProvider())).findFirst().orElseThrow(()->new IllegalStateException(environment+" IRP configuration has not been saved"));
   ensureComplete(selected); configs.forEach(c->c.setActive(c.getId().equals(selected.getId()))); repository.saveAll(configs);
   audit(selected,"ACTIVATED",environment+" environment activated."); events.publishEvent(new IrpConfigurationChangedEvent(companyId,environment)); return response(selected);
 }
 @Override public IrpConfigurationResponse testConnection(Long companyId,IrpEnvironment environment){throw new UnsupportedOperationException("Use authenticated test operation");}
 @Override @Transactional(readOnly=true) public IrpConfiguration getActiveConfiguration(Long companyId){return repository.findByCompanyIdAndActiveTrue(companyId).orElseThrow(()->new IllegalStateException("No active IRN / E-Invoice configuration. Configure and activate an environment in Settings."));}
 @Override @Transactional(readOnly=true) public IrpConfiguration getConfiguration(Long companyId,IrpEnvironment environment){return repository.findByCompanyIdAndProviderAndEnvironment(companyId,PROVIDER,environment).orElseThrow(()->new IllegalStateException(environment+" IRP configuration is not configured"));}
 @Override public IrpCredentials getActiveCredentials(Long companyId){return credentials(getActiveConfiguration(companyId));}
 @Override public IrpCredentials getDecryptedCredentials(Long companyId,IrpEnvironment environment){return credentials(getConfiguration(companyId,environment));}
 @Override @Transactional public IrpConfigurationResponse recordConnectionTest(Long companyId,IrpEnvironment environment,boolean success){var c=getConfiguration(companyId,environment); c.setLastConnectionTest(LocalDateTime.now()); c.setLastConnectionStatus(success?"SUCCESS":"FAILED"); repository.save(c); audit(c,"CONNECTION_TEST",success?"Connection successful.":"Connection failed."); return response(c);}
 private IrpCredentials credentials(IrpConfiguration c){ensureComplete(c); return new IrpCredentials(c.getId(),c.getCompanyId(),c.getProvider(),c.getEnvironment(),c.getApiBaseUrl(),encryption.decrypt(c.getClientIdEncrypted()),encryption.decrypt(c.getClientSecretEncrypted()),encryption.decrypt(c.getApiUsernameEncrypted()),encryption.decrypt(c.getApiPasswordEncrypted()),c.getGstin(),c.getApiVersion(),c.getCredentialVersion());}
 private String secret(String replacement,String existing,String label){if(StringUtils.hasText(replacement)) return encryption.encrypt(replacement.trim()); if(StringUtils.hasText(existing)) return existing; throw new IllegalArgumentException(label+" is required");}
 private void ensureComplete(IrpConfiguration c){if(!c.isConfigured()||!StringUtils.hasText(c.getApiBaseUrl())||!StringUtils.hasText(c.getGstin())||!StringUtils.hasText(c.getClientIdEncrypted())||!StringUtils.hasText(c.getClientSecretEncrypted())||!StringUtils.hasText(c.getApiUsernameEncrypted())||!StringUtils.hasText(c.getApiPasswordEncrypted())) throw new IllegalStateException(c.getEnvironment()+" IRP configuration is incomplete");}
 private void validateUrl(String value,IrpEnvironment environment){try{URI u=URI.create(value); if(!"https".equalsIgnoreCase(u.getScheme())||!StringUtils.hasText(u.getHost())) throw new Exception();}catch(Exception e){throw new IllegalArgumentException("API Base URL must be a valid HTTPS URL");}}
 private IrpConfigurationResponse response(IrpConfiguration c){return new IrpConfigurationResponse(c.getId(),c.getProvider(),c.getEnvironment(),c.getApiBaseUrl(),c.getGstin(),c.getApiVersion(),c.isActive(),c.isConfigured(),StringUtils.hasText(c.getClientIdEncrypted()),StringUtils.hasText(c.getClientSecretEncrypted()),StringUtils.hasText(c.getApiUsernameEncrypted()),StringUtils.hasText(c.getApiPasswordEncrypted()),c.getLastConnectionTest(),c.getLastConnectionStatus(),c.getUpdatedAt());}
 private void audit(IrpConfiguration c,String action,String details){var a=new IrpConfigurationAudit();a.setConfigurationId(c.getId());a.setCompanyId(c.getCompanyId());a.setProvider(c.getProvider());a.setEnvironment(c.getEnvironment());a.setAction(action);a.setPerformedBy(user());a.setPerformedAt(LocalDateTime.now());a.setDetails(details);audits.save(a);}
 private String user(){var a=SecurityContextHolder.getContext().getAuthentication();return a==null?"System":a.getName();}
 private String clean(String s){return StringUtils.hasText(s)?s.trim():null;}
 public record IrpConfigurationChangedEvent(Long companyId,IrpEnvironment environment){}
}
