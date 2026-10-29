package com.intelliatech.app.einvoice.qualification.repository;

import static com.intelliatech.app.einvoice.qualification.dto.QualificationModels.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository @RequiredArgsConstructor
public class QualificationStore {
 private final JdbcTemplate jdbc;
 public long start(String key,Suite suite){var h=new GeneratedKeyHolder();jdbc.update(c->{PreparedStatement p=c.prepareStatement("INSERT INTO ey_qualification_test_run(run_key,suite,status,started_at) VALUES(?,?,'RUNNING',?)",Statement.RETURN_GENERATED_KEYS);p.setString(1,key);p.setString(2,suite.name());p.setObject(3,LocalDateTime.now());return p;},h);return Objects.requireNonNull(h.getKey()).longValue();}
 public void result(long runId,Scenario s,Execution e,String result,String correlation,long duration){jdbc.update("INSERT INTO ey_qualification_test_result(test_run_id,scenario_id,api_name,scenario_name,expected_outcome,actual_outcome,result_status,http_status,provider_error_code,safe_message,correlation_id,duration_ms,executed_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",runId,s.id(),s.api().name(),s.name(),s.expected().name(),e.success()?"SUCCESS":"FAILURE",result,e.httpStatus(),e.errorCode(),truncate(e.message()),correlation,duration,LocalDateTime.now());}
 public void skipped(long runId,Scenario s,String correlation){result(runId,s,new Execution(false,0,"SKIPPED",s.skipReason(),null),"SKIPPED",correlation,0);}
 public void document(long runId,Scenario s,JsonNode fixture,Execution e){JsonNode d=e.data();String irn=d==null?null:d.path("Irn").asText(null);String ack=d==null?null:d.path("AckNo").asText(null);LocalDateTime ackDate=parseDate(d==null?null:d.path("AckDt").asText(null));jdbc.update("INSERT INTO ey_qualification_test_document(test_run_id,scenario_id,document_number,document_type,sandbox_irn,acknowledgement_number,acknowledgement_date,irn_status,created_at,updated_at) VALUES(?,?,?,?,?,?,?,'GENERATED',?,?)",runId,s.id(),fixture.path("DocDtls").path("No").asText(),fixture.path("DocDtls").path("Typ").asText(),irn,ack,ackDate,LocalDateTime.now(),LocalDateTime.now());}
 public Optional<Map<String,Object>> generatedForDetails(){return generated("AND details_retrieved=FALSE ORDER BY id");}
 public Optional<Map<String,Object>> generatedForCancellation(){return generated("ORDER BY details_retrieved DESC,id");}
 private Optional<Map<String,Object>> generated(String order){String sql="SELECT * FROM ey_qualification_test_document WHERE sandbox_irn IS NOT NULL AND cancellation_status='NOT_CANCELLED' "+order+" LIMIT 1";return jdbc.query(sql,(rs,n)->{Map<String,Object> m=new HashMap<>();m.put("id",rs.getLong("id"));m.put("irn",rs.getString("sandbox_irn"));return m;}).stream().findFirst();}
 public void details(long id){jdbc.update("UPDATE ey_qualification_test_document SET details_retrieved=TRUE,irn_status='DETAILS_VERIFIED',updated_at=? WHERE id=?",LocalDateTime.now(),id);}
 public void cancelled(long id){jdbc.update("UPDATE ey_qualification_test_document SET cancellation_status='CANCELLED',irn_status='CANCELLED',updated_at=? WHERE id=?",LocalDateTime.now(),id);}
 public boolean stopRequested(long id){return Boolean.TRUE.equals(jdbc.queryForObject("SELECT stop_requested FROM ey_qualification_test_run WHERE id=?",Boolean.class,id));}
 public void requestStop(long id){jdbc.update("UPDATE ey_qualification_test_run SET stop_requested=TRUE WHERE id=? AND status='RUNNING'",id);}
 public void finish(long id,String status,String summary){jdbc.update("UPDATE ey_qualification_test_run r SET status=?,completed_at=?,summary=?,total_scenarios=(SELECT COUNT(*) FROM ey_qualification_test_result x WHERE x.test_run_id=r.id),passed_count=(SELECT COUNT(*) FROM ey_qualification_test_result x WHERE x.test_run_id=r.id AND result_status='PASSED'),expected_failure_count=(SELECT COUNT(*) FROM ey_qualification_test_result x WHERE x.test_run_id=r.id AND result_status='EXPECTED_FAILURE'),unexpected_failure_count=(SELECT COUNT(*) FROM ey_qualification_test_result x WHERE x.test_run_id=r.id AND result_status='UNEXPECTED_FAILURE'),skipped_count=(SELECT COUNT(*) FROM ey_qualification_test_result x WHERE x.test_run_id=r.id AND result_status='SKIPPED') WHERE id=?",status,LocalDateTime.now(),summary,id);}
 public Map<String,Object> run(long id){return jdbc.queryForMap("SELECT * FROM ey_qualification_test_run WHERE id=?",id);}
 public List<Map<String,Object>> results(long id){return jdbc.queryForList("SELECT scenario_id,api_name,scenario_name,expected_outcome,actual_outcome,result_status,http_status,provider_error_code,safe_message,correlation_id,duration_ms,executed_at FROM ey_qualification_test_result WHERE test_run_id=? ORDER BY id",id);}
 public List<Map<String,Object>> documents(long id){return jdbc.queryForList("SELECT document_number,document_type,sandbox_irn,acknowledgement_number,acknowledgement_date,irn_status,details_retrieved,cancellation_status,created_at FROM ey_qualification_test_document WHERE test_run_id=? ORDER BY id",id);}
 public List<PortalProgress> portal(){return jdbc.query("SELECT * FROM ey_qualification_portal_progress ORDER BY api_name",(r,n)->new PortalProgress(r.getString("api_name"),r.getInt("target_success"),r.getInt("target_failure"),(Integer)r.getObject("portal_success"),(Integer)r.getObject("portal_failure"),r.getTimestamp("verified_at")==null?null:r.getTimestamp("verified_at").toLocalDateTime(),r.getString("verification_source"),r.getString("updated_by")));}
 public void portal(String api,int success,int failure,String source,String user){jdbc.update("UPDATE ey_qualification_portal_progress SET portal_success=?,portal_failure=?,verified_at=?,verification_source=?,updated_by=?,updated_at=? WHERE api_name=?",success,failure,LocalDateTime.now(),source,user,LocalDateTime.now(),api);}
 private String truncate(String v){return v==null?null:v.substring(0,Math.min(v.length(),1000));}
 private LocalDateTime parseDate(String v){if(v==null||v.isBlank())return null;try{return LocalDateTime.parse(v,java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));}catch(Exception e){return null;}}
}
