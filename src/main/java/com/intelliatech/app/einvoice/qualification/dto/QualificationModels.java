package com.intelliatech.app.einvoice.qualification.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class QualificationModels {
 private QualificationModels(){}
 public enum Api { AUTHENTICATE, GENERATE_IRN, CANCEL_IRN, GET_IRN_DETAILS }
 public enum Suite { AUTHENTICATE, GENERATE_IRN, CANCEL_IRN, GET_IRN_DETAILS, ALL }
 public enum Expected { SUCCESS, PROVIDER_FAILURE }
 public record Scenario(String id,Api api,String name,Expected expected,String fixture,boolean enabled,String skipReason){}
 public record Execution(boolean success,int httpStatus,String errorCode,String message,JsonNode data){}
 public record RunSummary(long id,String runKey,String suite,String status,int total,int passed,int expectedFailures,int unexpectedFailures,int skipped,LocalDateTime startedAt,LocalDateTime completedAt,List<Path> reports){}
 public record PortalProgress(String apiName,int targetSuccess,int targetFailure,Integer portalSuccess,Integer portalFailure,LocalDateTime verifiedAt,String verificationSource,String updatedBy){}
 public record Progress(Map<String,Object> local,List<PortalProgress> portal){}
}
