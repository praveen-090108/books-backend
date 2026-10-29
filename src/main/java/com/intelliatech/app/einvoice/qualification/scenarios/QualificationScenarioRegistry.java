package com.intelliatech.app.einvoice.qualification.scenarios;

import static com.intelliatech.app.einvoice.qualification.dto.QualificationModels.*;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class QualificationScenarioRegistry {
 private final List<Scenario> scenarios=List.of(
  new Scenario("AUTH-VALID",Api.AUTHENTICATE,"Valid sandbox authentication",Expected.SUCCESS,"valid",true,null),
  new Scenario("AUTH-INVALID-CLIENT",Api.AUTHENTICATE,"Invalid client ID",Expected.PROVIDER_FAILURE,"invalid-client",false,"Disabled by default to prevent credential lockout; enable only with provider approval."),
  new Scenario("GEN-B2B-INTER",Api.GENERATE_IRN,"B2B interstate service invoice",Expected.SUCCESS,"invoice",true,null),
  new Scenario("GEN-CREDIT-NOTE",Api.GENERATE_IRN,"B2B credit note",Expected.SUCCESS,"credit-note",true,null),
  new Scenario("GEN-MISSING-DOC",Api.GENERATE_IRN,"Missing document number",Expected.PROVIDER_FAILURE,"missing-document-number",true,null),
  new Scenario("GEN-INVALID-GSTIN",Api.GENERATE_IRN,"Invalid buyer GSTIN",Expected.PROVIDER_FAILURE,"invalid-gstin",true,null),
  new Scenario("GEN-MISSING-ITEMS",Api.GENERATE_IRN,"Missing item details",Expected.PROVIDER_FAILURE,"missing-items",true,null),
  new Scenario("GET-GENERATED-1",Api.GET_IRN_DETAILS,"Retrieve first runner-generated IRN",Expected.SUCCESS,"generated-document",true,null),
  new Scenario("GET-GENERATED-2",Api.GET_IRN_DETAILS,"Retrieve second runner-generated IRN",Expected.SUCCESS,"generated-document",true,null),
  new Scenario("GET-NONEXISTENT",Api.GET_IRN_DETAILS,"Retrieve nonexistent IRN",Expected.PROVIDER_FAILURE,"nonexistent-irn",true,null),
  new Scenario("CANCEL-GENERATED-1",Api.CANCEL_IRN,"Cancel first runner-generated IRN",Expected.SUCCESS,"generated-document",true,null),
  new Scenario("CANCEL-GENERATED-2",Api.CANCEL_IRN,"Cancel second runner-generated IRN",Expected.SUCCESS,"generated-document",true,null),
  new Scenario("CANCEL-NONEXISTENT",Api.CANCEL_IRN,"Cancel nonexistent IRN",Expected.PROVIDER_FAILURE,"nonexistent-irn",true,null)
 );
 public List<Scenario> forSuite(Suite suite){return scenarios.stream().filter(s->suite==Suite.ALL||s.api().name().equals(suite.name())).toList();}
 public Scenario byId(String id){return scenarios.stream().filter(s->s.id().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("Unknown qualification scenario: "+id));}
}
