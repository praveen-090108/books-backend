package com.intelliatech.app.einvoice.qualification.runner;

import static com.intelliatech.app.einvoice.qualification.dto.QualificationModels.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.intelliatech.app.einvoice.qualification.config.QualificationProperties;
import com.intelliatech.app.einvoice.qualification.fixtures.SandboxInvoiceFixtureFactory;
import com.intelliatech.app.einvoice.qualification.reporting.QualificationReportGenerator;
import com.intelliatech.app.einvoice.qualification.repository.QualificationStore;
import com.intelliatech.app.einvoice.qualification.scenarios.QualificationScenarioRegistry;
import com.intelliatech.app.einvoice.qualification.service.EySandboxApiExecutor;
import com.intelliatech.app.einvoice.qualification.validation.*;
import com.intelliatech.app.service.IrpConfigurationService;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor @Slf4j
public class SandboxQualificationRunner {
 private final SandboxSafetyGuard guard; private final QualificationProperties properties; private final QualificationScenarioRegistry registry; private final SandboxInvoiceFixtureFactory fixtures; private final EySandboxApiExecutor executor; private final QualificationResultValidator validator; private final QualificationStore store; private final QualificationReportGenerator reports; private final IrpConfigurationService configurations;
 public RunSummary run(Suite suite){return runScenarios(suite,registry.forSuite(suite));}
 public RunSummary runOne(String id){Scenario s=registry.byId(id);return runScenarios(Suite.valueOf(s.api().name()),List.of(s));}
 public RunSummary runPlan(String plan){List<Scenario> scenarios=new ArrayList<>();Api api=null;for(String entry:plan.split(",")){String[] parts=entry.trim().split(":");if(parts.length!=2)throw new IllegalArgumentException("Invalid scenario plan entry: "+entry);Scenario scenario=registry.byId(parts[0].trim());int count=Integer.parseInt(parts[1].trim());if(count<1)throw new IllegalArgumentException("Scenario count must be positive: "+entry);if(api!=null&&api!=scenario.api())throw new IllegalArgumentException("A scenario plan must contain one API type only");api=scenario.api();for(int i=0;i<count;i++)scenarios.add(scenario);}if(scenarios.isEmpty())throw new IllegalArgumentException("Scenario plan is empty");return runScenarios(Suite.valueOf(Objects.requireNonNull(api).name()),scenarios);}
 private RunSummary runScenarios(Suite suite,List<Scenario> requested){guard.verify();if(properties.getMaximumOperations()<1)throw new IllegalStateException("Maximum operations must be positive");List<Scenario> scenarios=requested.stream().limit(properties.getMaximumOperations()).toList();String key="EYQ-"+UUID.randomUUID().toString().substring(0,12).toUpperCase(Locale.ROOT);long runId=store.start(key,suite);LocalDateTime started=LocalDateTime.now();String finalStatus="COMPLETED";String summary="Qualification scenarios completed";
  try{for(Scenario scenario:scenarios){if(store.stopRequested(runId)){finalStatus="STOPPED";summary="Stop requested by administrator";break;}String correlation=UUID.randomUUID().toString();if(!scenario.enabled()){store.skipped(runId,scenario,correlation);continue;}long began=System.nanoTime();JsonNode fixture=null;Execution execution;
    try{switch(scenario.api()){
      case AUTHENTICATE -> execution=executor.authenticate();
      case GENERATE_IRN -> {String gstin=configurations.getDecryptedCredentials(1L,com.intelliatech.app.entity.IrpEnvironment.SANDBOX).gstin();fixture=fixtures.create(scenario.fixture(),gstin);validateFixture(fixture,scenario.expected());execution=executor.generate(fixture);}
      case GET_IRN_DETAILS -> {var document=scenario.fixture().equals("nonexistent-irn")?Optional.<Map<String,Object>>empty():store.generatedForDetails();String irn=scenario.fixture().equals("nonexistent-irn")?"0".repeat(64):String.valueOf(document.orElseThrow(()->new IllegalStateException("No unretrieved runner-generated IRN is available")).get("irn"));execution=executor.get(irn);if(execution.success()&&document.isPresent())store.details(((Number)document.get().get("id")).longValue());}
      case CANCEL_IRN -> {var document=scenario.fixture().equals("nonexistent-irn")?Optional.<Map<String,Object>>empty():store.generatedForCancellation();String irn=scenario.fixture().equals("nonexistent-irn")?"F".repeat(64):String.valueOf(document.orElseThrow(()->new IllegalStateException("No uncancelled runner-generated IRN is available")).get("irn"));execution=executor.cancel(irn);if(execution.success()&&document.isPresent())store.cancelled(((Number)document.get().get("id")).longValue());}
      default -> throw new IllegalStateException("Unsupported qualification API");}
    }catch(Exception e){execution=new Execution(false,0,"RUNNER_ERROR",safe(e),null);}
    long duration=(System.nanoTime()-began)/1_000_000;String result=validator.classify(scenario,execution);store.result(runId,scenario,execution,result,correlation,duration);if(scenario.api()==Api.GENERATE_IRN&&execution.success()&&fixture!=null)store.document(runId,scenario,fixture,execution);log.info("Qualification scenario completed run={} scenario={} api={} result={} httpStatus={} durationMs={}",key,scenario.id(),scenario.api(),result,execution.httpStatus(),duration);pause();}
  }catch(Exception e){finalStatus="FAILED";summary=safe(e);log.error("Qualification run failed run={} error={}",key,e.getClass().getSimpleName());}finally{store.finish(runId,finalStatus,summary);}
  List<java.nio.file.Path> paths=reports.generate(runId);Map<String,Object> row=store.run(runId);return new RunSummary(runId,key,suite.name(),String.valueOf(row.get("status")),number(row,"total_scenarios"),number(row,"passed_count"),number(row,"expected_failure_count"),number(row,"unexpected_failure_count"),number(row,"skipped_count"),started,LocalDateTime.now(),paths);
 }
 public void stop(long runId){store.requestStop(runId);}
 private void validateFixture(JsonNode n,Expected expected){if(expected==Expected.SUCCESS&&(n.path("DocDtls").path("No").asText().isBlank()||!n.path("ItemList").isArray()||n.path("ItemList").isEmpty()))throw new IllegalArgumentException("Locally invalid success fixture");}
 private void pause(){try{Thread.sleep(Math.max(0,properties.getDelayMillis()));}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("Qualification run interrupted");}}
 private int number(Map<String,Object> m,String k){Object v=m.get(k);return v instanceof Number n?n.intValue():0;}
 private String safe(Exception e){return e.getMessage()==null?e.getClass().getSimpleName():e.getMessage().replaceAll("(?i)(password|secret|token)\\s*[:=]\\s*[^,; ]+","$1=[REDACTED]");}
}
