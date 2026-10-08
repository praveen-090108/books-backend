package com.intelliatech.app.service.impl;

import com.fasterxml.jackson.databind.*;
import com.intelliatech.app.config.EInvoiceProperties;
import com.intelliatech.app.dto.response.*;
import com.intelliatech.app.entity.IrpEnvironment;
import com.intelliatech.app.service.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Service
public class EyIrpAuthenticationService implements IrpAuthenticationService {
 private static final Long COMPANY_ID=1L;
 private static final DateTimeFormatter EXPIRY=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
 private final EInvoiceProperties infrastructure; private final IrpConfigurationService configurations; private final RestClient restClient; private final ObjectMapper json; private final IrpCryptoService crypto; private final ResourceLoader resources;
 private final Map<String,IrpAuthenticationToken> tokens=new ConcurrentHashMap<>(); private final ReentrantLock lock=new ReentrantLock();
 public EyIrpAuthenticationService(EInvoiceProperties infrastructure,IrpConfigurationService configurations,@Qualifier("irpRestClient") RestClient restClient,ObjectMapper json,IrpCryptoService crypto,ResourceLoader resources){this.infrastructure=infrastructure;this.configurations=configurations;this.restClient=restClient;this.json=json;this.crypto=crypto;this.resources=resources;}
 @Override public IrpAuthenticationToken currentToken(){return current(configurations.getActiveCredentials(COMPANY_ID),false);}
 @Override public IrpAuthenticationToken currentToken(IrpEnvironment environment){return current(configurations.getDecryptedCredentials(COMPANY_ID,environment),false);}
 @Override public IrpAuthenticationToken refreshToken(){return current(configurations.getActiveCredentials(COMPANY_ID),true);}
 @Override public IrpAuthenticationToken refreshToken(IrpEnvironment environment){return current(configurations.getDecryptedCredentials(COMPANY_ID,environment),true);}
 private IrpAuthenticationToken current(IrpCredentials c,boolean force){String key=key(c);var existing=tokens.get(key);if(!force&&existing!=null&&!existing.expiresWithinMinutes(10))return existing;lock.lock();try{existing=tokens.get(key);if(!force&&existing!=null&&!existing.expiresWithinMinutes(10))return existing;return authenticate(c,key);}finally{lock.unlock();}}
 private IrpAuthenticationToken authenticate(IrpCredentials c,String key){try{byte[] appKey=crypto.generateAppKey();String payload=json.writeValueAsString(Map.of("UserName",c.apiUsername(),"Password",c.apiPassword(),"AppKey",Base64.getEncoder().encodeToString(appKey),"ForceRefreshAccessToken",true));String encrypted=crypto.encryptAuthenticationPayload(payload,loadPublicKey());JsonNode response=restClient.post().uri(c.authUrl()).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).header("client_id",c.clientId()).header("client_secret",c.clientSecret()).header("Gstin",c.gstin()).body(json.writeValueAsString(Map.of("Data",encrypted))).retrieve().body(JsonNode.class);if(response==null||response.path("Status").asInt(response.path("status").asInt())!=1)throw new IllegalStateException(error(response));JsonNode d=response.path("Data");var token=new IrpAuthenticationToken(required(d,"ClientId"),required(d,"UserName"),required(d,"AuthToken"),crypto.decryptSessionEncryptionKey(required(d,"Sek"),appKey),LocalDateTime.parse(required(d,"TokenExpiry"),EXPIRY));tokens.put(key,token);return token;}catch(IllegalStateException e){throw e;}catch(Exception e){throw new IllegalStateException("IRP authentication failed: "+safe(e));}}
 @Override public void invalidateToken(){tokens.clear();}
 @EventListener public void changed(IrpConfigurationServiceImpl.IrpConfigurationChangedEvent e){tokens.entrySet().removeIf(x->x.getKey().startsWith(e.companyId()+":"+e.environment()+":"));}
 private String key(IrpCredentials c){return c.companyId()+":"+c.environment()+":"+c.provider()+":"+c.gstin()+":"+c.credentialVersion();}
 private PublicKey loadPublicKey() throws Exception{String pem;try(var in=resources.getResource(infrastructure.publicKeyLocation()).getInputStream()){pem=new String(in.readAllBytes(),StandardCharsets.UTF_8);}String encoded=pem.replace("-----BEGIN PUBLIC KEY-----","").replace("-----END PUBLIC KEY-----","").replaceAll("\\s","");return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(encoded)));}
 private String error(JsonNode r){if(r==null)return "IRP authentication returned an empty response";JsonNode e=r.path("ErrorDetails");if(e.isMissingNode()||e.isNull())e=r.path("errorDetails");String details=e.isTextual()?e.asText():e.isMissingNode()||e.isNull()?r.path("InfoDtls").asText():e.toString();return "IRP authentication was rejected"+(StringUtils.hasText(details)?": "+details:"");}
 private String required(JsonNode n,String f){String v=n.path(f).asText();if(!StringUtils.hasText(v))throw new IllegalStateException("IRP authentication response is missing "+f);return v;}
 private String safe(Exception e){return StringUtils.hasText(e.getMessage())?e.getMessage():e.getClass().getSimpleName();}
}
