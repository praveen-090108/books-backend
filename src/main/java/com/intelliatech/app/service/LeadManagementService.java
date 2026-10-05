package com.intelliatech.app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intelliatech.app.exception.DuplicateResourceException;
import com.intelliatech.app.exception.ResourceConflictException;
import com.intelliatech.app.exception.ResourceNotFoundException;
import com.intelliatech.app.repository.AppUserRepository;
import com.intelliatech.app.security.CurrentUserService;
import com.intelliatech.app.security.DataScopeService;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class LeadManagementService {
    private static final long ORGANIZATION_ID = 1L;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final CurrentUserService currentUsers;
    private final DataScopeService dataScopes;
    private final AppUserRepository appUsers;

    public LeadManagementService(JdbcTemplate jdbc, ObjectMapper objectMapper, CurrentUserService currentUsers,
                                 DataScopeService dataScopes, AppUserRepository appUsers) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.currentUsers = currentUsers;
        this.dataScopes = dataScopes;
        this.appUsers = appUsers;
    }

    public Map<String,Object> options() {
        return map("pipelines", jdbc.queryForList("SELECT id,name,owner_id ownerId,color,visibility FROM lead_pipelines WHERE organization_id=? AND active=TRUE AND deleted=FALSE ORDER BY name", ORGANIZATION_ID),
                "stages", jdbc.queryForList("SELECT id,pipeline_id pipelineId,name,probability,color,stage_type stageType,sort_order sortOrder FROM pipeline_stages WHERE active=TRUE ORDER BY pipeline_id,sort_order"),
                "companies", jdbc.queryForList("SELECT c.id,c.company_name companyName FROM lead_companies c WHERE "+owned("c")+" AND c.deleted=FALSE ORDER BY c.company_name"),
                "contacts", jdbc.queryForList("SELECT id,CONCAT(first_name,' ',last_name) contactName,email,company_id companyId FROM lead_contacts WHERE organization_id=? AND deleted=FALSE ORDER BY first_name,last_name", ORGANIZATION_ID),
                "skills", jdbc.queryForList("SELECT id,skill_name name FROM skill_master WHERE organization_id=? AND active=TRUE AND deleted=FALSE ORDER BY skill_name", ORGANIZATION_ID),
                "reportEmployees", reportEmployees(),
                "dashboardEmployees", dashboardEmployees(),
                "reportCustomers", jdbc.queryForList("SELECT br.id,br.party_name name FROM business_records br WHERE LOWER(br.module)='sales' AND LOWER(br.type)='customers' AND LOWER(br.status)='active' AND "+businessRecordAccess("br")+" ORDER BY br.party_name"),
                "owners", jdbc.queryForList("SELECT owner_id FROM (SELECT owner_id FROM leads WHERE organization_id=? AND deleted=FALSE UNION SELECT owner_id FROM lead_companies WHERE organization_id=? AND deleted=FALSE UNION SELECT owner_id FROM lead_contacts WHERE organization_id=? AND deleted=FALSE) report_owners WHERE owner_id IS NOT NULL AND owner_id<>'' ORDER BY owner_id", String.class, ORGANIZATION_ID,ORGANIZATION_ID,ORGANIZATION_ID),
                "sources", jdbc.queryForList("SELECT source FROM (SELECT source FROM leads WHERE organization_id=? AND deleted=FALSE UNION SELECT lead_source source FROM lead_companies WHERE organization_id=? AND deleted=FALSE UNION SELECT lead_source source FROM lead_contacts WHERE organization_id=? AND deleted=FALSE) report_sources WHERE source IS NOT NULL AND source<>'' ORDER BY source", String.class, ORGANIZATION_ID,ORGANIZATION_ID,ORGANIZATION_ID));
    }

    /** Database-aggregated reporting payload shared by the Lead Reports categories. */
    public Map<String,Object> leadReport(String reportType, Map<String,String> filters) {
        LocalDate end=parseDate(filters.get("to"),LocalDate.now());
        LocalDate start=parseDate(filters.get("from"),end.minusDays(6));
        long days=Math.max(1,ChronoUnit.DAYS.between(start,end)+1);
        Map<String,String> currentFilters=new LinkedHashMap<>(filters);currentFilters.put("from",start.toString());currentFilters.put("to",end.toString());
        Map<String,String> previousFilters=new LinkedHashMap<>(filters);previousFilters.put("from",start.minusDays(days).toString());previousFilters.put("to",start.minusDays(1).toString());
        List<Object>args=new ArrayList<>(),previousArgs=new ArrayList<>();
        String where=reportFilter(currentFilters,args),previousWhere=reportFilter(previousFilters,previousArgs);
        String joins=" FROM leads l LEFT JOIN lead_companies c ON c.id=l.company_id LEFT JOIN lead_contacts ct ON ct.id=l.primary_contact_id JOIN lead_pipelines p ON p.id=l.pipeline_id JOIN pipeline_stages s ON s.id=l.stage_id LEFT JOIN skill_master sk ON sk.id=l.primary_skill_id ";
        String totalsSql="SELECT COUNT(DISTINCT l.id) totalLeads,"+
                "SUM(CASE WHEN UPPER(l.lead_type)='STAFFING' THEN 1 ELSE 0 END) staffingLeads,"+
                "SUM(CASE WHEN UPPER(l.lead_type)='FIXED_COST' THEN 1 ELSE 0 END) fixedCostLeads,"+
                "SUM(CASE WHEN UPPER(s.stage_type)='WON' OR UPPER(l.status) IN ('WON','CONVERTED') THEN 1 ELSE 0 END) wonLeads,"+
                "SUM(CASE WHEN UPPER(s.stage_type)='LOST' OR UPPER(l.status)='LOST' THEN 1 ELSE 0 END) lostLeads,"+
                "SUM(CASE WHEN UPPER(l.status)='REJECTED' THEN 1 ELSE 0 END) rejectedLeads,"+
                "SUM(CASE WHEN UPPER(s.stage_type)='OPEN' AND UPPER(l.status) NOT IN ('WON','LOST','CONVERTED','REJECTED') THEN 1 ELSE 0 END) openLeads,"+
                "SUM(CASE WHEN UPPER(s.name) LIKE '%PROPOSAL%' THEN 1 ELSE 0 END) proposalSent,"+
                "COALESCE(SUM(l.expected_value),0) pipelineValue,COALESCE(SUM(l.expected_value*l.probability/100),0) weightedPipeline,"+
                "COALESCE(AVG(DATEDIFF(COALESCE(l.converted_at,CURRENT_TIMESTAMP),l.created_at)),0) averageLeadAge "+joins+" WHERE ";
        Map<String,Object>current=one(totalsSql+where,args),previous=one(totalsSql+previousWhere,previousArgs);
        previous.forEach((key,value)->current.put("previous"+Character.toUpperCase(key.charAt(0))+key.substring(1),value));
        long closed=number(current.get("wonLeads"))+number(current.get("lostLeads"))+number(current.get("rejectedLeads"));
        current.put("winRate",percent(number(current.get("wonLeads")),closed));
        current.put("changes",changes(current,previous));
        current.put("newCompanies",periodCount("lead_companies",currentFilters));
        current.put("previousNewCompanies",periodCount("lead_companies",previousFilters));
        Map<String,Object>reportChanges=new LinkedHashMap<>((Map<String,Object>)current.get("changes"));
        double companyCurrent=number(current.get("newCompanies")),companyPrevious=number(current.get("previousNewCompanies"));
        reportChanges.put("newCompanies",companyPrevious==0?(companyCurrent==0?0:100):Math.round((companyCurrent-companyPrevious)*10000/companyPrevious)/100.0);
        current.put("changes",reportChanges);
        Map<String,Object>activityKpis=activityReportKpis(currentFilters);current.putAll(activityKpis);
        current.put("byEmployee",group("SELECT COALESCE(NULLIF(l.owner_id,''),'Unassigned') label,COUNT(*) value,SUM(CASE WHEN UPPER(s.stage_type)='WON' OR UPPER(l.status) IN ('WON','CONVERTED') THEN 1 ELSE 0 END) won,COALESCE(SUM(l.expected_value),0) amount "+joins+" WHERE "+where+" GROUP BY l.owner_id ORDER BY value DESC",args));
        current.put("byStatus",group("SELECT COALESCE(NULLIF(s.name,''),l.status) label,COUNT(*) value "+joins+" WHERE "+where+" GROUP BY s.name,l.status ORDER BY MIN(s.sort_order),value DESC",args));
        current.put("byType",group("SELECT REPLACE(l.lead_type,'_',' ') label,COUNT(*) value "+joins+" WHERE "+where+" GROUP BY l.lead_type ORDER BY value DESC",args));
        current.put("bySkill",group("SELECT COALESCE(sk.skill_name,'Not specified') label,COUNT(*) value "+joins+" WHERE "+where+" GROUP BY sk.skill_name ORDER BY value DESC LIMIT 10",args));
        current.put("bySource",group("SELECT COALESCE(NULLIF(l.source,''),'Unknown') label,COUNT(*) value "+joins+" WHERE "+where+" GROUP BY l.source ORDER BY value DESC",args));
        current.put("byWorkMode",group("SELECT COALESCE(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(l.type_details_json,'$.workMode')),''),'Not specified') label,COUNT(*) value "+joins+" WHERE "+where+" GROUP BY label ORDER BY value DESC",args));
        current.put("byStageValue",group("SELECT s.name label,COUNT(*) value,COALESCE(SUM(l.expected_value),0) amount "+joins+" WHERE "+where+" GROUP BY s.id,s.name,s.sort_order ORDER BY s.sort_order",args));
        current.put("trend",group("SELECT DATE(l.created_at) label,COUNT(*) value "+joins+" WHERE "+where+" GROUP BY DATE(l.created_at) ORDER BY label",args));
        current.put("previousTrend",group("SELECT DATE(l.created_at) label,COUNT(*) value "+joins+" WHERE "+previousWhere+" GROUP BY DATE(l.created_at) ORDER BY label",previousArgs));
        current.put("newCompaniesTable",group("SELECT c.id,c.company_name companyName,c.created_by addedBy,c.created_at createdAt,(SELECT COUNT(*) FROM leads cl WHERE cl.company_id=c.id AND cl.deleted=FALSE) leads FROM lead_companies c WHERE c.organization_id=? AND c.deleted=FALSE AND DATE(c.created_at) BETWEEN ? AND ? ORDER BY c.created_at DESC LIMIT 5",List.of(ORGANIZATION_ID,Date.valueOf(start),Date.valueOf(end))));
        current.put("followUps",group("SELECT l.id,l.lead_name leadName,c.company_name companyName,l.owner_id owner,MAX(a.occurred_at) lastActivity,DATEDIFF(CURRENT_DATE,COALESCE(MAX(a.occurred_at),l.created_at)) days "+joins+" LEFT JOIN lead_activities a ON a.entity_type='LEAD' AND a.entity_id=l.id AND a.organization_id=l.organization_id WHERE "+where+" AND UPPER(s.stage_type)='OPEN' GROUP BY l.id,l.lead_name,c.company_name,l.owner_id,l.created_at ORDER BY days DESC LIMIT 5",args));
        current.put("overdueActivities",group("SELECT t.id,t.title activity,l.lead_name leadName,COALESCE(t.assigned_user,t.created_by) owner,t.due_date dueDate FROM lead_tasks t JOIN leads l ON l.id=t.entity_id AND t.entity_type='LEAD' WHERE t.organization_id=? AND t.status<>'COMPLETED' AND t.due_date<CURRENT_TIMESTAMP AND "+access("l")+" ORDER BY t.due_date LIMIT 5",List.of(ORGANIZATION_ID)));
        current.put("reportType",reportType);current.put("from",start);current.put("to",end);current.put("generatedAt",LocalDateTime.now());
        return current;
    }

    public String leadReportCsv(String reportType,Map<String,String>filters){
        Map<String,Object>r=leadReport(reportType,filters);StringBuilder csv=new StringBuilder("Metric,Value\n");
        for(String key:List.of("totalLeads","newCompanies","staffingLeads","fixedCostLeads","wonLeads","lostLeads","rejectedLeads","openLeads","proposalSent","pipelineValue","weightedPipeline","winRate","averageLeadAge","profilesSubmitted","interviewsScheduled","candidatesSelected","candidatesJoined"))csv.append(csvValue(key)).append(',').append(csvValue(r.get(key))).append('\n');
        return csv.toString();
    }
    private String csvValue(Object value){String text=value==null?"":String.valueOf(value);return text.contains(",")||text.contains("\"")||text.contains("\n")?"\""+text.replace("\"","\"\"")+"\"":text;}

    private String reportFilter(Map<String,String>f,List<Object>a){
        String w=access("l")+" AND l.deleted=FALSE";
        if(StringUtils.hasText(f.get("from"))){w+=" AND DATE(l.created_at)>=?";a.add(Date.valueOf(f.get("from")));}
        if(StringUtils.hasText(f.get("to"))){w+=" AND DATE(l.created_at)<=?";a.add(Date.valueOf(f.get("to")));}
        w=eq(w,a,"l.owner_id",accessibleReportOwner(first(f,"employee","owner")));w=eq(w,a,"l.pipeline_id",f.get("pipelineId"));w=eq(w,a,"l.lead_type",f.get("leadType"));w=eq(w,a,"l.status",f.get("status"));w=eq(w,a,"l.source",f.get("source"));w=eq(w,a,"l.primary_skill_id",f.get("primarySkillId"));w=eq(w,a,"l.company_id",f.get("companyId"));w=eq(w,a,"l.stage_id",f.get("stageId"));w=eq(w,a,"c.industry",f.get("industry"));w=eq(w,a,"c.country",f.get("country"));w=eq(w,a,"c.city",f.get("city"));
        if(StringUtils.hasText(f.get("customerName"))){w+=" AND LOWER(c.company_name)=LOWER(?)";a.add(f.get("customerName").trim());}
        if(StringUtils.hasText(f.get("workMode"))){w+=" AND UPPER(JSON_UNQUOTE(JSON_EXTRACT(l.type_details_json,'$.workMode')))=?";a.add(f.get("workMode").toUpperCase(Locale.ROOT));}
        return w;
    }
    private List<Map<String,Object>> reportEmployees(){
        var scope=dataScopes.getCurrentDataScope();
        String restriction=scope.unrestricted()?"":" AND u.id IN ("+accessibleUserIdSql()+")";
        return jdbc.queryForList("SELECT u.id userId,r.id employeeId,r.party_name name,u.id value,u.role_name roleName FROM app_users u JOIN business_records r ON r.id=u.resource_id WHERE LOWER(u.status)='active' AND LOWER(r.status)='active' AND LOWER(r.module)='resources' AND LOWER(r.type)='resources'"+restriction+" ORDER BY r.party_name");
    }
    private List<Map<String,Object>> dashboardEmployees(){
        var scope=dataScopes.getCurrentDataScope();
        String restriction=scope.unrestricted()?"":" AND u.id IN ("+accessibleUserIdSql()+")";
        String eligibility=scope.unrestricted()?eligibleAssigneeRoleSql():"(u.id="+currentUsers.getCurrentUserId()+" OR "+eligibleAssigneeRoleSql()+")";
        return jdbc.queryForList("SELECT u.id userId,r.id employeeId,r.party_name name,u.id value,u.role_name roleName FROM app_users u JOIN business_records r ON r.id=u.resource_id WHERE LOWER(u.status)='active' AND LOWER(r.status)='active' AND LOWER(r.module)='resources' AND LOWER(r.type)='resources' AND "+eligibility+restriction+" ORDER BY r.party_name");
    }
    private String accessibleReportOwner(String requested){
        if(!StringUtils.hasText(requested))return null;
        List<Map<String,Object>> matches;
        try{matches=jdbc.queryForList("SELECT u.id,COALESCE(NULLIF(r.party_email,''),u.email) loginEmail,u.status userStatus,r.status resourceStatus FROM app_users u JOIN business_records r ON r.id=u.resource_id WHERE u.id=?",Long.valueOf(requested.trim()));}
        catch(NumberFormatException ignored){matches=jdbc.queryForList("SELECT u.id,COALESCE(NULLIF(r.party_email,''),u.email) loginEmail,u.status userStatus,r.status resourceStatus FROM app_users u JOIN business_records r ON r.id=u.resource_id WHERE LOWER(COALESCE(NULLIF(r.party_email,''),u.email))=LOWER(?)",requested.trim());}
        if(matches.isEmpty())throw new org.springframework.security.access.AccessDeniedException("The selected employee is not available for this report.");
        Map<String,Object> selected=matches.get(0);Long selectedId=((Number)selected.get("id")).longValue();
        var scope=dataScopes.getCurrentDataScope();
        if(!scope.unrestricted()&&!scope.accessibleUserIds().contains(selectedId))throw new org.springframework.security.access.AccessDeniedException("You are not authorized to view reports for the selected employee.");
        if(!"active".equalsIgnoreCase(String.valueOf(selected.get("userStatus")))||!"active".equalsIgnoreCase(String.valueOf(selected.get("resourceStatus"))))throw new org.springframework.security.access.AccessDeniedException("The selected employee is not active.");
        return String.valueOf(selected.get("loginEmail"));
    }
    private String accessibleDashboardOwner(String requested){
        if(!StringUtils.hasText(requested))return null;
        List<Map<String,Object>> selected;
        try{selected=jdbc.queryForList("SELECT u.role_name roleName FROM app_users u WHERE u.id=?",Long.valueOf(requested.trim()));}
        catch(NumberFormatException ignored){selected=jdbc.queryForList("SELECT u.role_name roleName FROM app_users u LEFT JOIN business_records r ON r.id=u.resource_id WHERE LOWER(COALESCE(NULLIF(r.party_email,''),u.email))=LOWER(?)",requested.trim());}
        boolean selectingSelf=false;
        try{selectingSelf=Long.valueOf(requested.trim()).equals(currentUsers.getCurrentUserId());}catch(NumberFormatException ignored){}
        if(selected.isEmpty()||(!selectingSelf&&!eligibleAssigneeRole(String.valueOf(selected.get(0).get("roleName")))))
            throw new org.springframework.security.access.AccessDeniedException("The selected employee is not eligible for the Lead Dashboard.");
        return accessibleReportOwner(requested);
    }
    private boolean eligibleAssigneeRole(String roleName){
        String normalized=roleName==null?"":roleName.trim().toUpperCase(Locale.ROOT);
        return normalized.startsWith("SALE")||normalized.startsWith("VENDOR")||normalized.startsWith("VENDER");
    }
    private Map<String,Object>activityReportKpis(Map<String,String>f){LocalDate from=parseDate(f.get("from"),LocalDate.now().minusDays(6)),to=parseDate(f.get("to"),LocalDate.now());Map<String,Object>m=one("SELECT SUM(CASE WHEN UPPER(activity_type) LIKE '%PROFILE%' THEN 1 ELSE 0 END) profilesSubmitted,SUM(CASE WHEN UPPER(activity_type) LIKE '%INTERVIEW%' THEN 1 ELSE 0 END) interviewsScheduled,SUM(CASE WHEN UPPER(activity_type) LIKE '%SELECT%' THEN 1 ELSE 0 END) candidatesSelected,SUM(CASE WHEN UPPER(activity_type) LIKE '%JOIN%' THEN 1 ELSE 0 END) candidatesJoined FROM lead_activities WHERE organization_id=? AND entity_type='LEAD' AND DATE(occurred_at) BETWEEN ? AND ?",List.of(ORGANIZATION_ID,Date.valueOf(from),Date.valueOf(to)));return m;}
    private long periodCount(String table,Map<String,String>f){return jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE organization_id=? AND deleted=FALSE AND DATE(created_at) BETWEEN ? AND ?",Long.class,ORGANIZATION_ID,Date.valueOf(f.get("from")),Date.valueOf(f.get("to")));}
    private LocalDate parseDate(String value,LocalDate fallback){try{return StringUtils.hasText(value)?LocalDate.parse(value):fallback;}catch(Exception ignored){return fallback;}}
    private String first(Map<String,String>values,String...keys){for(String key:keys)if(StringUtils.hasText(values.get(key)))return values.get(key);return null;}

    public Map<String,Object> dashboard(LocalDate from, LocalDate to, Long pipelineId, String owner, String source, String status) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        long days = Math.max(1, ChronoUnit.DAYS.between(start, end) + 1);
        LocalDate previousEnd = start.minusDays(1), previousStart = previousEnd.minusDays(days - 1);
        List<Object> args = new ArrayList<>();
        String scopedOwner=accessibleDashboardOwner(owner);
        String filter = leadFilter(start, end, pipelineId, scopedOwner, source, status, args, "l");
        List<Object> previousArgs = new ArrayList<>();
        String previousFilter = leadFilter(previousStart, previousEnd, pipelineId, scopedOwner, source, status, previousArgs, "l");
        Map<String,Object> current = one("SELECT COUNT(*) totalLeads,SUM(CASE WHEN UPPER(l.status)='NEW' THEN 1 ELSE 0 END) newLeads," +
                "SUM(CASE WHEN UPPER(l.status)='QUALIFIED' THEN 1 ELSE 0 END) qualifiedLeads,SUM(CASE WHEN UPPER(l.status)='CONVERTED' THEN 1 ELSE 0 END) convertedLeads," +
                "SUM(CASE WHEN UPPER(l.status)='LOST' THEN 1 ELSE 0 END) lostLeads,COALESCE(SUM(l.expected_value*l.probability/100),0) weightedValue," +
                "COALESCE(SUM(CASE WHEN UPPER(l.status) IN ('WON','CONVERTED') THEN l.expected_value ELSE 0 END),0) wonRevenue," +
                "COALESCE(AVG(DATEDIFF(COALESCE(l.converted_at,NOW()),l.created_at)),0) averageLeadAge FROM leads l WHERE " + filter, args);
        Map<String,Object> previous = one("SELECT COUNT(*) totalLeads,SUM(CASE WHEN UPPER(l.status)='NEW' THEN 1 ELSE 0 END) newLeads," +
                "SUM(CASE WHEN UPPER(l.status)='QUALIFIED' THEN 1 ELSE 0 END) qualifiedLeads,SUM(CASE WHEN UPPER(l.status)='CONVERTED' THEN 1 ELSE 0 END) convertedLeads," +
                "SUM(CASE WHEN UPPER(l.status)='LOST' THEN 1 ELSE 0 END) lostLeads FROM leads l WHERE " + previousFilter, previousArgs);
        current.put("changes", changes(current, previous));
        current.put("conversionRate", percent(number(current.get("convertedLeads")), number(current.get("totalLeads"))));
        current.put("byPipeline", group("SELECT p.id,p.name label,COUNT(*) value,COALESCE(SUM(l.expected_value),0) amount FROM leads l JOIN lead_pipelines p ON p.id=l.pipeline_id WHERE " + filter + " GROUP BY p.id,p.name ORDER BY value DESC", args));
        current.put("byStatus", group("SELECT l.status label,COUNT(*) value FROM leads l WHERE " + filter + " GROUP BY l.status ORDER BY value DESC", args));
        current.put("bySource", group("SELECT l.source label,COUNT(*) value,SUM(CASE WHEN UPPER(l.status)='CONVERTED' THEN 1 ELSE 0 END) converted FROM leads l WHERE " + filter + " GROUP BY l.source ORDER BY value DESC", args));
        current.put("byType", group("SELECT l.lead_type label,COUNT(*) value FROM leads l WHERE " + filter + " GROUP BY l.lead_type ORDER BY value DESC", args));
        current.put("byOwner", group("SELECT l.owner_id label,COUNT(*) value,SUM(CASE WHEN UPPER(l.status)='CONVERTED' THEN 1 ELSE 0 END) converted FROM leads l WHERE " + filter + " GROUP BY l.owner_id ORDER BY value DESC", args));
        current.put("overTime", group("SELECT DATE(l.created_at) label,COUNT(*) value,SUM(CASE WHEN UPPER(l.status)='CONVERTED' THEN 1 ELSE 0 END) converted FROM leads l WHERE " + filter + " GROUP BY DATE(l.created_at) ORDER BY label", args));
        current.put("recentLeads", group(baseLeadSelect() + " WHERE " + filter + " ORDER BY l.created_at DESC LIMIT 6", args));
        return current;
    }

    public Map<String,Object> leads(Map<String,String> filters, int page, int size) {
        List<Object> args = new ArrayList<>();
        String where = access("l") + " AND l.deleted=FALSE";
        if (StringUtils.hasText(filters.get("search"))) { where += " AND (LOWER(l.lead_name) LIKE ? OR LOWER(c.company_name) LIKE ? OR LOWER(CONCAT(ct.first_name,' ',ct.last_name)) LIKE ?)"; String q="%"+filters.get("search").toLowerCase()+"%"; args.add(q);args.add(q);args.add(q); }
        where = eq(where,args,"l.status",filters.get("status")); where=eq(where,args,"l.lead_type",filters.get("type")); where=eq(where,args,"l.pipeline_id",filters.get("pipelineId")); where=eq(where,args,"l.primary_skill_id",filters.get("primarySkillId")); where=eq(where,args,"l.source",filters.get("source")); where=eq(where,args,"l.owner_id",filters.get("owner"));
        if (StringUtils.hasText(filters.get("from"))) { where += " AND DATE(l.created_at)>=?"; args.add(Date.valueOf(filters.get("from"))); }
        if (StringUtils.hasText(filters.get("to"))) { where += " AND DATE(l.created_at)<=?"; args.add(Date.valueOf(filters.get("to"))); }
        String joins=" LEFT JOIN lead_companies c ON c.id=l.company_id LEFT JOIN lead_contacts ct ON ct.id=l.primary_contact_id JOIN lead_pipelines p ON p.id=l.pipeline_id JOIN pipeline_stages s ON s.id=l.stage_id ";
        return page(baseLeadSelect()+" WHERE "+where+" ORDER BY l.created_at DESC", "SELECT COUNT(*) FROM leads l "+joins+" WHERE "+where,args,page,size);
    }

    public Map<String,Object> lead(long id) {
        List<Map<String,Object>> rows = jdbc.queryForList(baseLeadSelect()+" WHERE l.id=? AND "+access("l")+" AND l.deleted=FALSE", id);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Lead not found or not accessible.");
        Map<String,Object> lead = new LinkedHashMap<>(rows.get(0));
        Object rawTypeDetails=lead.remove("typeDetailsJson");
        try{lead.put("typeDetails",rawTypeDetails==null||!StringUtils.hasText(String.valueOf(rawTypeDetails))?new LinkedHashMap<>():objectMapper.readValue(String.valueOf(rawTypeDetails),Map.class));}
        catch(JsonProcessingException ignored){lead.put("typeDetails",new LinkedHashMap<>());}
        lead.put("contacts", jdbc.queryForList("SELECT c.id,CONCAT(c.first_name,' ',c.last_name) contactName,c.job_title jobTitle,c.email,c.phone,m.primary_contact primaryContact FROM lead_contact_mapping m JOIN lead_contacts c ON c.id=m.contact_id WHERE m.lead_id=?",id));
        lead.put("activities", support("lead_activities", "LEAD", id)); lead.put("tasks", support("lead_tasks", "LEAD", id)); lead.put("notes", support("lead_notes", "LEAD", id)); lead.put("attachments", support("lead_attachments", "LEAD", id));
        lead.put("deals", jdbc.queryForList("SELECT d.id,d.deal_name dealName,p.name pipeline,s.name stage,d.amount,d.probability,d.expected_close_date expectedCloseDate,d.owner_id owner,d.status FROM lead_deals d JOIN lead_pipelines p ON p.id=d.pipeline_id JOIN pipeline_stages s ON s.id=d.stage_id WHERE d.lead_id=? AND d.deleted=FALSE",id));
        lead.put("history", jdbc.queryForList("SELECT h.id,fs.name fromStage,ts.name toStage,h.previous_probability previousProbability,h.new_probability newProbability,h.reason,h.changed_by changedBy,h.changed_at changedAt FROM lead_stage_history h LEFT JOIN pipeline_stages fs ON fs.id=h.from_stage_id JOIN pipeline_stages ts ON ts.id=h.to_stage_id WHERE h.lead_id=? ORDER BY h.changed_at DESC",id));
        lead.put("timeline", jdbc.queryForList("SELECT eventSource,id,eventType,title,description,actor,eventAt FROM (" +
                "SELECT 'ACTIVITY' eventSource,a.id,a.activity_type eventType,a.title,a.description,a.created_by actor,a.occurred_at eventAt FROM lead_activities a WHERE a.organization_id=? AND a.entity_type='LEAD' AND a.entity_id=? " +
                "UNION ALL SELECT 'AUDIT',al.id,al.action,CONCAT('Lead ',LOWER(al.action)),NULL,al.performed_by,al.performed_at FROM lead_audit_log al WHERE al.organization_id=? AND al.entity_type='LEAD' AND al.entity_id=?" +
                ") events ORDER BY eventAt DESC",ORGANIZATION_ID,id,ORGANIZATION_ID,id));
        return lead;
    }

    public List<Map<String,Object>> eligibleAssignees(){
        return jdbc.queryForList("SELECT u.id userId,r.id employeeId,r.party_name name,u.role_name roleName FROM app_users u JOIN business_records r ON r.id=u.resource_id WHERE LOWER(u.status)='active' AND LOWER(r.status)='active' AND LOWER(r.module)='resources' AND "+eligibleAssigneeRoleSql()+" ORDER BY r.party_name");
    }

    public List<Map<String,Object>> profileVendors(){
        return jdbc.queryForList("SELECT id,vendor_name name FROM vendors WHERE organization_id=? AND UPPER(status)='ACTIVE' AND UPPER(REPLACE(REPLACE(TRIM(vendor_type),'_',' '),'-',' '))='IT SERVICE PROVIDER' ORDER BY vendor_name",ORGANIZATION_ID);
    }

    @Transactional public Map<String,Object> assignLead(long id,Map<String,Object> body){
        assertLeadAccess(id);
        long employeeId=longValue(body.get("employeeId"));
        Map<String,Object> target=oneRequired("SELECT u.id userId,r.party_name name FROM app_users u JOIN business_records r ON r.id=u.resource_id WHERE r.id=? AND LOWER(u.status)='active' AND LOWER(r.status)='active' AND LOWER(r.module)='resources' AND "+eligibleAssigneeRoleSql(),List.of(employeeId),"Selected employee is not eligible for Lead assignment.");
        Map<String,Object> current=oneRequired("SELECT l.assigned_user_id assignedUserId,l.lead_name leadName FROM leads l WHERE l.id=? AND "+access("l")+" AND l.deleted=FALSE",List.of(id),"Lead not found or not accessible.");
        Long previous=nullableLong(current.get("assignedUserId"));long next=longValue(target.get("userId"));long actor=currentUsers.getCurrentUserId();String note=text(body.get("note"),null);
        jdbc.update("UPDATE leads SET assigned_user_id=?,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",next,user(),id);
        jdbc.update("INSERT INTO lead_assignment_history(lead_id,previous_assignee_id,new_assignee_id,assigned_by_user_id,assignment_note) VALUES(?,?,?,?,?)",id,previous,next,actor,note);
        String previousName=previous==null?"Unassigned":jdbc.queryForObject("SELECT COALESCE(r.party_name,u.name) FROM app_users u LEFT JOIN business_records r ON r.id=u.resource_id WHERE u.id=?",String.class,previous);
        String targetName=String.valueOf(target.get("name"));String actorName=currentUsers.getCurrentUser().displayName();boolean reassigned=previous!=null;
        activity("LEAD",id,reassigned?"LEAD_REASSIGNED":"LEAD_ASSIGNED",reassigned?"Lead reassigned":"Lead assigned",previousName+" → "+targetName+" · Assigned by "+actorName+(note==null?"":" · "+note));
        jdbc.update("INSERT INTO app_notifications(recipient_user_id,notification_type,lead_id,assigned_by_user_id,title,message,target_path) VALUES(?,?,?,?,?,?,?)",next,reassigned?"LEAD_REASSIGNED":"LEAD_ASSIGNED",id,actor,reassigned?"Lead Reassigned":"New Lead Assigned","\""+current.get("leadName")+"\" has been assigned to you by "+actorName+(note==null?"":". Note: "+note),"/lead-management/leads/"+id);
        // Do not reload through lead(id) after transferring the assignment. A
        // normal assignee can legitimately hand a lead to an eligible teammate,
        // but may no longer be inside that lead's data scope immediately after
        // the update. Reloading here would throw and roll back the transaction.
        // Return the assignment result assembled from the records already
        // validated inside this transaction instead.
        return map(
                "id", id,
                "leadName", current.get("leadName"),
                "previousAssigneeId", previous,
                "assignedUserId", next,
                "assignedUserName", targetName,
                "assignedByUserId", actor,
                "assignmentNote", note,
                "reassigned", reassigned,
                "message", "Lead assigned successfully to " + targetName + "."
        );
    }

    /**
     * Role names are administrator-managed labels. Existing installations use both
     * Sale/Sales and Vender/Vendor, so eligibility must normalize those variants
     * instead of relying on one hard-coded spelling.
     */
    private String eligibleAssigneeRoleSql() {
        return "(UPPER(TRIM(u.role_name)) LIKE 'SALE%' OR UPPER(TRIM(u.role_name)) LIKE 'VENDOR%' OR UPPER(TRIM(u.role_name)) LIKE 'VENDER%')";
    }

    public Map<String,Object> leadNotifications(){long userId=currentUsers.getCurrentUserId();ensureStaffingSowNotifications(userId);List<Map<String,Object>> rows=jdbc.queryForList("SELECT id,notification_type notificationType,lead_id leadId,project_record_id projectRecordId,title,message,target_path targetPath,is_read isRead,created_at createdAt FROM app_notifications WHERE recipient_user_id=? ORDER BY created_at DESC LIMIT 30",userId);Integer unread=jdbc.queryForObject("SELECT COUNT(*) FROM app_notifications WHERE recipient_user_id=? AND is_read=FALSE",Integer.class,userId);return map("content",rows,"unreadCount",unread==null?0:unread);}
    @Transactional public void markLeadNotificationRead(long id){jdbc.update("UPDATE app_notifications SET is_read=TRUE,read_at=CURRENT_TIMESTAMP WHERE id=? AND recipient_user_id=?",id,currentUsers.getCurrentUserId());}

    /** Creates one durable reminder per user, staffing assignment and SOW end date. */
    @Transactional
    public void ensureStaffingSowNotifications(long userId) {
        var current = currentUsers.getCurrentUser();
        String access = currentUsers.isAdmin(current) ? "" : " AND br.created_by=" + userId;
        List<Map<String,Object>> expiring = jdbc.queryForList("""
                SELECT br.id,br.due_date,
                       COALESCE(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(br.notes,'$.workingResource')),''),
                                NULLIF(JSON_UNQUOTE(JSON_EXTRACT(br.notes,'$.selectedResource')),''),
                                br.owner_name,'Resource') resource_name
                FROM business_records br
                WHERE br.module='projects' AND br.type='staffing' AND UPPER(br.status)='ACTIVE'
                  AND br.due_date BETWEEN CURRENT_DATE AND DATE_ADD(CURRENT_DATE, INTERVAL 20 DAY)
                """ + access);
        DateTimeFormatter displayDate = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
        for (Map<String,Object> item : expiring) {
            long projectId = ((Number)item.get("id")).longValue();
            LocalDate dueDate = ((Date)item.get("due_date")).toLocalDate();
            String resource = String.valueOf(item.get("resource_name"));
            String reference = "STAFFING_SOW_EXPIRY:" + projectId + ":" + dueDate;
            jdbc.update("""
                    INSERT IGNORE INTO app_notifications
                    (recipient_user_id,notification_type,reference_key,project_record_id,title,message,target_path)
                    VALUES(?,?,?,?,?,?,?)
                    """, userId, "STAFFING_SOW_EXPIRY", reference, projectId,
                    "Staffing SOW renewal required",
                    resource + "'s SOW will end on " + dueDate.format(displayDate) + ". Please renew it.",
                    "/project/staffing/" + projectId);
        }
    }

    public Map<String,Object> leadTimeline(long id,String type,int page,int size){
        assertLeadAccess(id);List<Object>args=new ArrayList<>(List.of(ORGANIZATION_ID,id,ORGANIZATION_ID,id,ORGANIZATION_ID,id,ORGANIZATION_ID,id));
        String sql="SELECT eventSource,id,eventType,title,description,actor,eventAt FROM ("+
                "SELECT 'ACTIVITY' eventSource,a.id,a.activity_type eventType,a.title,a.description,a.created_by actor,a.occurred_at eventAt FROM lead_activities a WHERE a.organization_id=? AND a.entity_type='LEAD' AND a.entity_id=? "+
                "UNION ALL SELECT 'TASK',t.id,CONCAT('TASK_',t.status),CONCAT('Task ',LOWER(t.status)),t.title,t.created_by,t.updated_at FROM lead_tasks t WHERE t.organization_id=? AND t.entity_type='LEAD' AND t.entity_id=? "+
                "UNION ALL SELECT 'NOTE',n.id,'NOTE_ADDED','Note added',n.content,n.created_by,n.created_at FROM lead_notes n WHERE n.organization_id=? AND n.entity_type='LEAD' AND n.entity_id=? AND n.deleted=FALSE "+
                "UNION ALL SELECT 'AUDIT',al.id,al.action,CONCAT('Lead ',LOWER(al.action)),NULL,al.performed_by,al.performed_at FROM lead_audit_log al WHERE al.organization_id=? AND al.entity_type='LEAD' AND al.entity_id=?"+
                ") events";
        if(StringUtils.hasText(type)&&!"ALL".equalsIgnoreCase(type)){sql+=" WHERE UPPER(eventType) LIKE ?";args.add("%"+type.toUpperCase(Locale.ROOT)+"%");}
        return page(sql+" ORDER BY eventAt DESC","SELECT COUNT(*) FROM ("+sql+") timeline_count",args,page,size);
    }

    public Map<String,Object> leadActivities(long id,String type){assertLeadAccess(id);List<Object>args=new ArrayList<>(List.of(ORGANIZATION_ID,id));String where="a.organization_id=? AND a.entity_type='LEAD' AND a.entity_id=?";if(StringUtils.hasText(type)){where+=" AND UPPER(a.activity_type)=?";args.add(type.toUpperCase(Locale.ROOT));}List<Map<String,Object>>rows=jdbc.queryForList("SELECT a.id,a.activity_type activityType,a.title,a.description,a.vendor_profile vendorProfile,a.vendor_id vendorId,v.vendor_name vendorName,a.occurred_at occurredAt,a.created_by owner,a.created_by createdBy FROM lead_activities a LEFT JOIN vendors v ON v.id=a.vendor_id WHERE "+where+" ORDER BY a.occurred_at DESC",args.toArray());return map("content",rows,"totalElements",rows.size());}
    public Map<String,Object> leadTasks(long id){assertLeadAccess(id);List<Map<String,Object>>rows=support("lead_tasks","LEAD",id);return map("content",rows,"totalElements",rows.size());}
    public Map<String,Object> leadStageHistory(long id){assertLeadAccess(id);List<Map<String,Object>>rows=jdbc.queryForList("SELECT historyRow.id,historyRow.stage,historyRow.amount,historyRow.enteredAt,historyRow.exitedAt,TIMESTAMPDIFF(DAY,historyRow.enteredAt,COALESCE(historyRow.exitedAt,CURRENT_TIMESTAMP)) durationDays,historyRow.exitedAt IS NULL currentStage,historyRow.modifiedBy,historyRow.previousStage,historyRow.changeSource FROM (SELECT h.id,ts.name stage,l.expected_value amount,h.changed_at enteredAt,LEAD(h.changed_at) OVER (ORDER BY h.changed_at) exitedAt,h.changed_by modifiedBy,fs.name previousStage,h.reason changeSource FROM lead_stage_history h JOIN leads l ON l.id=h.lead_id JOIN pipeline_stages ts ON ts.id=h.to_stage_id LEFT JOIN pipeline_stages fs ON fs.id=h.from_stage_id WHERE h.lead_id=?) historyRow ORDER BY historyRow.enteredAt DESC",id);return map("content",rows,"totalElements",rows.size());}

    @Transactional public Map<String,Object> saveLead(Long id, Map<String,Object> body, boolean draft) {
        require(body,"leadName","Lead name is required."); require(body,"leadType","Lead type is required."); require(body,"pipelineId","Pipeline is required."); require(body,"stageId","Stage is required."); require(body,"source","Source is required.");
        if (!draft) require(body,"primarySkillId","Please select a Primary Skill.");
        Long primarySkillId=nullableLong(body.get("primarySkillId"));
        if(primarySkillId!=null&&jdbc.queryForObject("SELECT COUNT(*) FROM skill_master WHERE id=? AND organization_id=? AND active=TRUE AND deleted=FALSE",Integer.class,primarySkillId,ORGANIZATION_ID)==0) throw new IllegalArgumentException("Please select a valid Primary Skill.");
        String secondarySkill=text(body.get("secondarySkill"),null); if(secondarySkill!=null)secondarySkill=secondarySkill.trim(); if(secondarySkill!=null&&secondarySkill.length()>500)throw new IllegalArgumentException("Secondary Skill must not exceed 500 characters.");
        String actor=user(), owner=text(body.get("ownerId"),actor); long pipeline=longValue(body.get("pipelineId")); long stage=longValue(body.get("stageId"));
        Map<String,Object> stageRow=oneRequired("SELECT pipeline_id pipelineId,probability,stage_type stageType,name FROM pipeline_stages WHERE id=? AND active=TRUE",List.of(stage),"Pipeline stage not found.");
        if (number(stageRow.get("pipelineId"))!=pipeline) throw new IllegalArgumentException("Selected stage does not belong to the pipeline.");
        int probability=body.get("probability")==null?number(stageRow.get("probability")):number(body.get("probability"));
        String status=text(body.get("status"),String.valueOf(stageRow.get("name"))).toUpperCase(Locale.ROOT).replace(' ','_');
        try {
            if (id==null) {
                String number="LEAD-"+System.currentTimeMillis();
                id=insert("INSERT INTO leads (organization_id,lead_number,lead_name,lead_type,primary_skill_id,secondary_skill,pipeline_id,stage_id,status,source,owner_id,expected_value,expected_close_date,probability,lead_score,next_step,priority,description,company_id,primary_contact_id,type_details_json,tags,draft,created_by,updated_by) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                        ORGANIZATION_ID,number,text(body.get("leadName"),""),text(body.get("leadType"),"FIXED_COST"),primarySkillId,secondarySkill,pipeline,stage,status,text(body.get("source"),"Other"),owner,decimal(body.get("expectedValue")),date(body.get("expectedCloseDate")),probability,number(body.get("leadScore")),text(body.get("nextStep"),null),text(body.get("priority"),"MEDIUM"),text(body.get("description"),null),nullableLong(body.get("companyId")),nullableLong(body.get("primaryContactId")),json(body.get("typeDetails")),text(body.get("tags"),null),draft,actor,actor);
                stageHistory(id,null,stage,null,probability,"Lead created",actor); activity("LEAD",id,"LEAD_CREATED","Lead created",text(body.get("description"),""));
            } else {
                assertLeadAccess(id); Map<String,Object> old=one("SELECT stage_id stageId,probability FROM leads WHERE id=?",List.of(id));
                jdbc.update("UPDATE leads SET lead_name=?,lead_type=?,primary_skill_id=?,secondary_skill=?,pipeline_id=?,stage_id=?,status=?,source=?,owner_id=?,expected_value=?,expected_close_date=?,probability=?,lead_score=?,next_step=?,priority=?,description=?,company_id=?,primary_contact_id=?,type_details_json=?,tags=?,draft=?,updated_by=? WHERE id=?",
                        text(body.get("leadName"),""),text(body.get("leadType"),"FIXED_COST"),primarySkillId,secondarySkill,pipeline,stage,status,text(body.get("source"),"Other"),owner,decimal(body.get("expectedValue")),date(body.get("expectedCloseDate")),probability,number(body.get("leadScore")),text(body.get("nextStep"),null),text(body.get("priority"),"MEDIUM"),text(body.get("description"),null),nullableLong(body.get("companyId")),nullableLong(body.get("primaryContactId")),json(body.get("typeDetails")),text(body.get("tags"),null),draft,actor,id);
                if (number(old.get("stageId"))!=stage) stageHistory(id,number(old.get("stageId")),stage,number(old.get("probability")),probability,"Lead updated",actor);
                activity("LEAD",id,"STATUS_CHANGE","Lead updated","Lead details were updated.");
            }
            syncLeadContacts(id,body); audit("LEAD",id,id==null?"CREATE":"UPDATE",body);
            return lead(id);
        } catch (DuplicateKeyException e) { throw new DuplicateResourceException("A duplicate lead already exists."); }
    }

    @Transactional public Map<String,Object> changeStage(long id, Map<String,Object> body) {
        assertLeadAccess(id); long stage=longValue(body.get("stageId")); Map<String,Object> old=oneRequired("SELECT l.stage_id stageId,l.probability,p.require_lost_reason requireLostReason,p.auto_probability autoProbability FROM leads l JOIN lead_pipelines p ON p.id=l.pipeline_id WHERE l.id=?",List.of(id),"Lead not found.");
        Map<String,Object> target=oneRequired("SELECT s.id,s.pipeline_id pipelineId,s.name,s.probability,s.stage_type stageType FROM pipeline_stages s JOIN leads l ON l.pipeline_id=s.pipeline_id WHERE s.id=? AND l.id=?",List.of(stage,id),"Invalid stage for this lead.");
        // Selecting the already-current stage is a no-op, not a transition.
        if(number(old.get("stageId"))==stage) return lead(id);
        String type=String.valueOf(target.get("stageType")); String reason=text(body.get("reason"),null);
        if ("LOST".equals(type) && Boolean.TRUE.equals(old.get("requireLostReason")) && !StringUtils.hasText(reason)) throw new IllegalArgumentException("Lost reason is required.");
        int probability=Boolean.TRUE.equals(old.get("autoProbability"))?number(target.get("probability")):number(old.get("probability"));
        String status="WON".equals(type)?"WON":"LOST".equals(type)?"LOST":String.valueOf(target.get("name")).toUpperCase().replace(' ','_');
        jdbc.update("UPDATE leads SET stage_id=?,status=?,probability=?,lost_reason=?,updated_by=? WHERE id=?",stage,status,probability,reason,user(),id);
        stageHistory(id,number(old.get("stageId")),stage,number(old.get("probability")),probability,reason,user()); activity("LEAD",id,"STAGE_CHANGE","Stage changed","Moved to "+target.get("name")); return lead(id);
    }

    @Transactional public Map<String,Object> changeOwner(long id, String owner) { assertLeadAccess(id); jdbc.update("UPDATE leads SET owner_id=?,updated_by=? WHERE id=?",owner,user(),id); activity("LEAD",id,"OWNER_CHANGE","Owner changed","Assigned to "+owner); return lead(id); }
    @Transactional public void deleteLead(long id) { assertLeadAccess(id); jdbc.update("UPDATE leads SET deleted=TRUE,active=FALSE,updated_by=? WHERE id=?",user(),id); audit("LEAD",id,"DELETE",Map.of()); }

    @Transactional public Map<String,Object> convert(long id, Map<String,Object> body) {
        assertLeadAccess(id); Map<String,Object> lead=oneRequired("SELECT * FROM leads WHERE id=? AND deleted=FALSE",List.of(id),"Lead not found.");
        if (lead.get("converted_deal_id")!=null) throw new ResourceConflictException("Lead has already been converted.");
        require(body,"dealName","Deal name is required.");
        long pipeline=body.get("pipelineId")==null?number(lead.get("pipeline_id")):longValue(body.get("pipelineId")); long stage=longValue(body.get("stageId")); String actor=user();
        Map<String,Object> target=oneRequired("SELECT s.id,s.pipeline_id pipelineId,s.name,s.probability,s.stage_type stageType FROM pipeline_stages s JOIN lead_pipelines p ON p.id=s.pipeline_id WHERE s.id=? AND s.active=TRUE AND p.active=TRUE AND p.deleted=FALSE AND p.organization_id=?",List.of(stage,ORGANIZATION_ID),"Conversion stage not found.");
        if (number(target.get("pipelineId"))!=pipeline) throw new IllegalArgumentException("Conversion stage does not belong to the selected pipeline.");
        int probability=body.get("probability")==null?number(target.get("probability")):number(body.get("probability"));
        if(probability<0||probability>100) throw new IllegalArgumentException("Probability must be between 0 and 100.");
        String dealStatus="WON".equals(target.get("stageType"))?"WON":"LOST".equals(target.get("stageType"))?"LOST":"OPEN";
        long deal=insert("INSERT INTO lead_deals (organization_id,lead_id,deal_name,pipeline_id,stage_id,amount,probability,expected_close_date,owner_id,status,created_by,updated_by) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",ORGANIZATION_ID,id,text(body.get("dealName"),String.valueOf(lead.get("lead_name"))),pipeline,stage,decimal(body.get("amount")),probability,date(body.get("expectedCloseDate")),text(body.get("ownerId"),String.valueOf(lead.get("owner_id"))),dealStatus,actor,actor);
        jdbc.update("UPDATE leads SET pipeline_id=?,stage_id=?,probability=?,status='CONVERTED',converted_deal_id=?,converted_at=NOW(),updated_by=? WHERE id=?",pipeline,stage,probability,deal,actor,id);
        if(number(lead.get("stage_id"))!=stage) stageHistory(id,number(lead.get("stage_id")),stage,number(lead.get("probability")),probability,"Lead converted",actor);
        activity("LEAD",id,"STATUS_CHANGE","Lead converted","Converted to deal #"+deal); return lead(id);
    }

    public Map<String,Object> pipelines(String search,int page,int size) {
        List<Object> args=new ArrayList<>(); String where="p.organization_id=? AND p.deleted=FALSE";args.add(ORGANIZATION_ID);
        if(StringUtils.hasText(search)){where+=" AND LOWER(p.name) LIKE ?";args.add("%"+search.toLowerCase()+"%");}
        // Pipelines and their stages are shared configuration within an
        // organization. Their lead counts must still respect record-level data
        // scope so configuration visibility never leaks another user's Leads.
        String select="SELECT p.id,p.name,p.description,p.owner_id ownerId,p.currency_code currencyCode,p.pipeline_type pipelineType,p.visibility,p.default_probability defaultProbability,p.color,p.active,p.archived,p.created_at createdAt,COUNT(l.id) leads,SUM(CASE WHEN s.stage_type='OPEN' THEN 1 ELSE 0 END) openDeals,SUM(CASE WHEN s.stage_type='WON' THEN 1 ELSE 0 END) wonDeals,SUM(CASE WHEN s.stage_type='LOST' THEN 1 ELSE 0 END) lostDeals FROM lead_pipelines p LEFT JOIN leads l ON l.pipeline_id=p.id AND l.deleted=FALSE AND "+access("l")+" LEFT JOIN pipeline_stages s ON s.id=l.stage_id WHERE "+where+" GROUP BY p.id";
        return page(select+" ORDER BY p.created_at DESC","SELECT COUNT(*) FROM lead_pipelines p WHERE "+where,args,page,size);
    }
    public Map<String,Object> pipeline(long id){Map<String,Object> p=new LinkedHashMap<>(oneRequired("SELECT id,name,description,owner_id ownerId,currency_code currencyCode,expected_close_date expectedCloseDate,pipeline_type pipelineType,visibility,default_probability defaultProbability,color,allow_duplicate_stages allowDuplicateStages,auto_probability autoProbability,require_stage_age requireStageAge,require_lost_reason requireLostReason,allow_closed_edit allowClosedEdit,active,archived,created_at createdAt FROM lead_pipelines p WHERE id=? AND p.organization_id=? AND deleted=FALSE",List.of(id,ORGANIZATION_ID),"Pipeline not found or not accessible."));p.put("stages",jdbc.queryForList("SELECT id,name,probability,expected_duration_days expectedDurationDays,color,stage_type stageType,sort_order sortOrder,active FROM pipeline_stages WHERE pipeline_id=? AND active=TRUE ORDER BY sort_order",id));return p;}
    @Transactional public Map<String,Object> savePipeline(Long id,Map<String,Object> b){require(b,"name","Pipeline name is required.");List<?> stages=(List<?>)b.getOrDefault("stages",List.of());long won=stages.stream().filter(x->"WON".equals(text(((Map<?,?>)x).get("stageType"),"OPEN"))).count(),lost=stages.stream().filter(x->"LOST".equals(text(((Map<?,?>)x).get("stageType"),"OPEN"))).count();if(won!=1||lost!=1)throw new IllegalArgumentException("Pipeline requires exactly one Won stage and one Lost stage.");String actor=user();try{if(id==null)id=insert("INSERT INTO lead_pipelines (organization_id,name,description,owner_id,currency_code,expected_close_date,pipeline_type,visibility,default_probability,color,allow_duplicate_stages,auto_probability,require_stage_age,require_lost_reason,allow_closed_edit,active,created_by,updated_by) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",ORGANIZATION_ID,text(b.get("name"),""),text(b.get("description"),null),text(b.get("ownerId"),actor),text(b.get("currencyCode"),"INR"),date(b.get("expectedCloseDate")),text(b.get("pipelineType"),"SALES"),text(b.get("visibility"),"ALL_USERS"),number(b.get("defaultProbability")),text(b.get("color"),"#ef1f2c"),bool(b.get("allowDuplicateStages")),boolDefault(b.get("autoProbability"),true),bool(b.get("requireStageAge")),boolDefault(b.get("requireLostReason"),true),bool(b.get("allowClosedEdit")),boolDefault(b.get("active"),true),actor,actor);else jdbc.update("UPDATE lead_pipelines SET name=?,description=?,owner_id=?,currency_code=?,expected_close_date=?,pipeline_type=?,visibility=?,default_probability=?,color=?,allow_duplicate_stages=?,auto_probability=?,require_stage_age=?,require_lost_reason=?,allow_closed_edit=?,active=?,updated_by=? WHERE id=? AND organization_id=?",text(b.get("name"),""),text(b.get("description"),null),text(b.get("ownerId"),actor),text(b.get("currencyCode"),"INR"),date(b.get("expectedCloseDate")),text(b.get("pipelineType"),"SALES"),text(b.get("visibility"),"ALL_USERS"),number(b.get("defaultProbability")),text(b.get("color"),"#ef1f2c"),bool(b.get("allowDuplicateStages")),boolDefault(b.get("autoProbability"),true),bool(b.get("requireStageAge")),boolDefault(b.get("requireLostReason"),true),bool(b.get("allowClosedEdit")),boolDefault(b.get("active"),true),actor,id,ORGANIZATION_ID);syncStages(id,stages);return pipeline(id);}catch(DuplicateKeyException e){throw new DuplicateResourceException("A pipeline with this name already exists.");}}
    @Transactional public Map<String,Object> clonePipeline(long id){Map<String,Object> p=pipeline(id);p.put("name",p.get("name")+" Copy");p.remove("id");if(p.get("stages") instanceof List<?> stages)p.put("stages",stages.stream().map(raw->{Map<String,Object> stage=new LinkedHashMap<>((Map<String,Object>)raw);stage.remove("id");return stage;}).toList());return savePipeline(null,p);} 
    @Transactional public void deletePipeline(long id){pipeline(id);Integer leads=jdbc.queryForObject("SELECT COUNT(*) FROM leads WHERE pipeline_id=? AND deleted=FALSE",Integer.class,id);if(leads!=null&&leads>0)throw new ResourceConflictException("Pipeline contains leads and cannot be deleted.");jdbc.update("UPDATE lead_pipelines SET deleted=TRUE,active=FALSE,updated_by=? WHERE id=? AND organization_id=?",user(),id,ORGANIZATION_ID);}
    public Map<String,Object> kanban(long pipelineId){Map<String,Object> p=pipeline(pipelineId);List<Map<String,Object>> stages=(List<Map<String,Object>>)p.get("stages");for(Map<String,Object>s:stages){s.put("leads",jdbc.queryForList(baseLeadSelect()+" WHERE l.pipeline_id=? AND l.stage_id=? AND "+access("l")+" AND l.deleted=FALSE ORDER BY l.created_at DESC LIMIT 100",pipelineId,s.get("id")));s.put("totalValue",jdbc.queryForObject("SELECT COALESCE(SUM(expected_value),0) FROM leads l WHERE pipeline_id=? AND stage_id=? AND "+access("l")+" AND deleted=FALSE",BigDecimal.class,pipelineId,s.get("id")));}p.put("summary",one("SELECT COUNT(*) totalLeads,COALESCE(SUM(expected_value),0) pipelineValue,COALESCE(SUM(expected_value*probability/100),0) weightedValue,COALESCE(AVG(DATEDIFF(NOW(),created_at)),0) averageAge,SUM(CASE WHEN status IN ('WON','CONVERTED') THEN 1 ELSE 0 END)*100/NULLIF(COUNT(*),0) conversionRate FROM leads l WHERE pipeline_id=? AND "+access("l")+" AND deleted=FALSE",List.of(pipelineId)));return p;}

    public Map<String,Object> companies(Map<String,String> f,int page,int size){List<Object>a=new ArrayList<>();String w="c.organization_id=? AND c.deleted=FALSE";a.add(ORGANIZATION_ID);if(StringUtils.hasText(f.get("search"))){w+=" AND (LOWER(c.company_name) LIKE ? OR LOWER(COALESCE(c.email,'')) LIKE ? OR LOWER(COALESCE(c.phone,'')) LIKE ? OR LOWER(COALESCE(c.website,'')) LIKE ?)";String q="%"+f.get("search").toLowerCase()+"%";a.add(q);a.add(q);a.add(q);a.add(q);}w=eq(w,a,"c.status",f.get("status"));w=eq(w,a,"c.industry",f.get("industry"));w=eq(w,a,"c.country",f.get("country"));w=eq(w,a,"c.city",f.get("city"));w=eq(w,a,"c.owner_id",f.get("owner"));if(StringUtils.hasText(f.get("from"))){w+=" AND DATE(c.created_at)>=?";a.add(Date.valueOf(f.get("from")));}if(StringUtils.hasText(f.get("to"))){w+=" AND DATE(c.created_at)<=?";a.add(Date.valueOf(f.get("to")));}String s="SELECT c.id,c.company_name companyName,c.website,c.industry,c.company_type companyType,c.email,c.phone,c.owner_id ownerId,c.status,c.created_at createdAt,COUNT(ct.id) contacts FROM lead_companies c LEFT JOIN lead_contacts ct ON ct.company_id=c.id AND ct.deleted=FALSE WHERE "+w+" GROUP BY c.id";return page(s+" ORDER BY c.created_at DESC","SELECT COUNT(*) FROM lead_companies c WHERE "+w,a,page,size);}
    public Map<String,Object> company(long id){Map<String,Object>c=new LinkedHashMap<>(oneRequired("SELECT id,company_name companyName,website,industry,company_type companyType,email,phone,other_phone otherPhone,fax,gstin,pan,currency_code currencyCode,ownership,employee_range employeeRange,annual_revenue annualRevenue,lead_source leadSource,status,description,primary_contact_id primaryContactId,street_address streetAddress,address_line2 addressLine2,city,state,country,postal_code postalCode,linkedin_url linkedinUrl,facebook_url facebookUrl,twitter_url twitterUrl,tags,notes,owner_id ownerId,created_at createdAt,updated_at updatedAt FROM lead_companies WHERE id=? AND organization_id=? AND deleted=FALSE",List.of(id,ORGANIZATION_ID),"Company not found."));c.put("contacts",jdbc.queryForList("SELECT id,CONCAT(first_name,' ',last_name) contactName,job_title jobTitle,email,phone,contact_type contactType FROM lead_contacts WHERE company_id=? AND deleted=FALSE",id));c.put("leads",jdbc.queryForList(baseLeadSelect()+" WHERE l.company_id=? AND l.deleted=FALSE",id));c.put("activities",support("lead_activities","COMPANY",id));c.put("tasks",support("lead_tasks","COMPANY",id));c.put("notesList",support("lead_notes","COMPANY",id));return c;}
    @Transactional public Map<String,Object> saveCompany(Long id,Map<String,Object>b){require(b,"companyName","Company name is required.");String actor=user();try{if(id==null)id=insert("INSERT INTO lead_companies (organization_id,company_name,website,industry,company_type,email,phone,other_phone,fax,gstin,pan,currency_code,ownership,employee_range,annual_revenue,lead_source,status,description,primary_contact_id,street_address,address_line2,city,state,country,postal_code,linkedin_url,facebook_url,twitter_url,tags,notes,owner_id,created_by,updated_by) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",ORGANIZATION_ID,text(b.get("companyName"),""),text(b.get("website"),null),text(b.get("industry"),null),text(b.get("companyType"),null),text(b.get("email"),null),text(b.get("phone"),null),text(b.get("otherPhone"),null),text(b.get("fax"),null),text(b.get("gstin"),null),text(b.get("pan"),null),text(b.get("currencyCode"),"INR"),text(b.get("ownership"),null),text(b.get("employeeRange"),null),decimal(b.get("annualRevenue")),text(b.get("leadSource"),null),text(b.get("status"),"ACTIVE"),text(b.get("description"),null),nullableLong(b.get("primaryContactId")),text(b.get("streetAddress"),null),text(b.get("addressLine2"),null),text(b.get("city"),null),text(b.get("state"),null),text(b.get("country"),null),text(b.get("postalCode"),null),text(b.get("linkedinUrl"),null),text(b.get("facebookUrl"),null),text(b.get("twitterUrl"),null),text(b.get("tags"),null),text(b.get("notes"),null),text(b.get("ownerId"),actor),actor,actor);else{jdbc.update("UPDATE lead_companies SET company_name=?,website=?,industry=?,company_type=?,email=?,phone=?,other_phone=?,fax=?,gstin=?,pan=?,currency_code=?,ownership=?,employee_range=?,annual_revenue=?,lead_source=?,status=?,description=?,primary_contact_id=?,street_address=?,address_line2=?,city=?,state=?,country=?,postal_code=?,linkedin_url=?,facebook_url=?,twitter_url=?,tags=?,notes=?,owner_id=?,updated_by=? WHERE id=? AND organization_id=?",text(b.get("companyName"),""),text(b.get("website"),null),text(b.get("industry"),null),text(b.get("companyType"),null),text(b.get("email"),null),text(b.get("phone"),null),text(b.get("otherPhone"),null),text(b.get("fax"),null),text(b.get("gstin"),null),text(b.get("pan"),null),text(b.get("currencyCode"),"INR"),text(b.get("ownership"),null),text(b.get("employeeRange"),null),decimal(b.get("annualRevenue")),text(b.get("leadSource"),null),text(b.get("status"),"ACTIVE"),text(b.get("description"),null),nullableLong(b.get("primaryContactId")),text(b.get("streetAddress"),null),text(b.get("addressLine2"),null),text(b.get("city"),null),text(b.get("state"),null),text(b.get("country"),null),text(b.get("postalCode"),null),text(b.get("linkedinUrl"),null),text(b.get("facebookUrl"),null),text(b.get("twitterUrl"),null),text(b.get("tags"),null),text(b.get("notes"),null),text(b.get("ownerId"),actor),actor,id,ORGANIZATION_ID);}audit("COMPANY",id,id==null?"CREATE":"UPDATE",b);return company(id);}catch(DuplicateKeyException e){throw new DuplicateResourceException("A company with this name already exists.");}}
    @Transactional public void deleteCompany(long id){Integer linked=jdbc.queryForObject("SELECT COUNT(*) FROM leads WHERE company_id=? AND deleted=FALSE",Integer.class,id);if(linked!=null&&linked>0)throw new ResourceConflictException("Company is linked to active leads.");jdbc.update("UPDATE lead_companies SET deleted=TRUE,active=FALSE,updated_by=? WHERE id=?",user(),id);}

    public Map<String,Object> contacts(Map<String,String>f,int page,int size){List<Object>a=new ArrayList<>();String w="c.organization_id=? AND c.deleted=FALSE";a.add(ORGANIZATION_ID);if(StringUtils.hasText(f.get("search"))){w+=" AND (LOWER(c.first_name) LIKE ? OR LOWER(c.last_name) LIKE ? OR LOWER(c.email) LIKE ? OR LOWER(c.phone) LIKE ? OR LOWER(co.company_name) LIKE ?)";String q="%"+f.get("search").toLowerCase()+"%";a.add(q);a.add(q);a.add(q);a.add(q);a.add(q);}w=eq(w,a,"c.contact_type",f.get("type"));w=eq(w,a,"c.company_id",f.get("companyId"));w=eq(w,a,"c.owner_id",f.get("owner"));if(StringUtils.hasText(f.get("tag"))){w+=" AND LOWER(COALESCE(c.tags,'')) LIKE ?";a.add("%"+f.get("tag").toLowerCase()+"%");}String s="SELECT c.id,CONCAT(c.first_name,' ',c.last_name) contactName,c.first_name firstName,c.last_name lastName,c.job_title jobTitle,c.email,c.phone,c.lead_source source,c.owner_id ownerId,c.tags,c.status,c.created_at createdAt,co.company_name companyName,co.id companyId FROM lead_contacts c LEFT JOIN lead_companies co ON co.id=c.company_id WHERE "+w;return page(s+" ORDER BY c.created_at DESC","SELECT COUNT(*) FROM lead_contacts c LEFT JOIN lead_companies co ON co.id=c.company_id WHERE "+w,a,page,size);}
    public Map<String,Object> contact(long id){Map<String,Object>c=new LinkedHashMap<>(oneRequired("SELECT c.id,c.first_name firstName,c.last_name lastName,c.job_title jobTitle,c.email,c.phone,c.other_phone otherPhone,c.department,c.date_of_birth dateOfBirth,c.assistant_name assistantName,c.reports_to_id reportsToId,c.owner_id ownerId,c.lead_source leadSource,c.contact_type contactType,c.company_id companyId,co.company_name companyName,c.tags,c.street_address streetAddress,c.address_line2 addressLine2,c.city,c.state,c.country,c.postal_code postalCode,c.skype_id skypeId,c.linkedin_url linkedinUrl,c.twitter_url twitterUrl,c.description,c.notes,c.status,c.created_at createdAt FROM lead_contacts c LEFT JOIN lead_companies co ON co.id=c.company_id WHERE c.id=? AND c.organization_id=? AND c.deleted=FALSE",List.of(id,ORGANIZATION_ID),"Contact not found."));c.put("leads",jdbc.queryForList(baseLeadSelect()+" JOIN lead_contact_mapping lm ON lm.lead_id=l.id WHERE lm.contact_id=? AND l.deleted=FALSE",id));c.put("activities",support("lead_activities","CONTACT",id));c.put("tasks",support("lead_tasks","CONTACT",id));c.put("notesList",support("lead_notes","CONTACT",id));return c;}
    @Transactional public Map<String,Object> saveContact(Long id,Map<String,Object>b){require(b,"firstName","First name is required.");require(b,"lastName","Last name is required.");require(b,"email","Email is required.");require(b,"phone","Phone is required.");String actor=user();try{if(id==null)id=insert("INSERT INTO lead_contacts (organization_id,first_name,last_name,job_title,email,phone,other_phone,department,date_of_birth,assistant_name,reports_to_id,owner_id,lead_source,contact_type,company_id,tags,street_address,address_line2,city,state,country,postal_code,skype_id,linkedin_url,twitter_url,description,notes,status,created_by,updated_by) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",ORGANIZATION_ID,text(b.get("firstName"),""),text(b.get("lastName"),""),text(b.get("jobTitle"),null),text(b.get("email"),""),text(b.get("phone"),""),text(b.get("otherPhone"),null),text(b.get("department"),null),date(b.get("dateOfBirth")),text(b.get("assistantName"),null),nullableLong(b.get("reportsToId")),text(b.get("ownerId"),actor),text(b.get("leadSource"),null),text(b.get("contactType"),"OTHER"),nullableLong(b.get("companyId")),text(b.get("tags"),null),text(b.get("streetAddress"),null),text(b.get("addressLine2"),null),text(b.get("city"),null),text(b.get("state"),null),text(b.get("country"),null),text(b.get("postalCode"),null),text(b.get("skypeId"),null),text(b.get("linkedinUrl"),null),text(b.get("twitterUrl"),null),text(b.get("description"),null),text(b.get("notes"),null),text(b.get("status"),"ACTIVE"),actor,actor);else jdbc.update("UPDATE lead_contacts SET first_name=?,last_name=?,job_title=?,email=?,phone=?,other_phone=?,department=?,date_of_birth=?,assistant_name=?,reports_to_id=?,owner_id=?,lead_source=?,contact_type=?,company_id=?,tags=?,street_address=?,address_line2=?,city=?,state=?,country=?,postal_code=?,skype_id=?,linkedin_url=?,twitter_url=?,description=?,notes=?,status=?,updated_by=? WHERE id=? AND organization_id=?",text(b.get("firstName"),""),text(b.get("lastName"),""),text(b.get("jobTitle"),null),text(b.get("email"),""),text(b.get("phone"),""),text(b.get("otherPhone"),null),text(b.get("department"),null),date(b.get("dateOfBirth")),text(b.get("assistantName"),null),nullableLong(b.get("reportsToId")),text(b.get("ownerId"),actor),text(b.get("leadSource"),null),text(b.get("contactType"),"OTHER"),nullableLong(b.get("companyId")),text(b.get("tags"),null),text(b.get("streetAddress"),null),text(b.get("addressLine2"),null),text(b.get("city"),null),text(b.get("state"),null),text(b.get("country"),null),text(b.get("postalCode"),null),text(b.get("skypeId"),null),text(b.get("linkedinUrl"),null),text(b.get("twitterUrl"),null),text(b.get("description"),null),text(b.get("notes"),null),text(b.get("status"),"ACTIVE"),actor,id,ORGANIZATION_ID);if(nullableLong(b.get("companyId"))!=null)jdbc.update("INSERT IGNORE INTO company_contact_mapping(company_id,contact_id,primary_contact) VALUES(?,?,?)",nullableLong(b.get("companyId")),id,"PRIMARY".equals(text(b.get("contactType"),"OTHER")));return contact(id);}catch(DuplicateKeyException e){throw new DuplicateResourceException("A contact with this email already exists.");}}
    @Transactional public void deleteContact(long id){Integer linked=jdbc.queryForObject("SELECT COUNT(*) FROM lead_contact_mapping m JOIN leads l ON l.id=m.lead_id WHERE m.contact_id=? AND l.deleted=FALSE",Integer.class,id);if(linked!=null&&linked>0)throw new ResourceConflictException("Contact is linked to active leads.");jdbc.update("UPDATE lead_contacts SET deleted=TRUE,active=FALSE,updated_by=? WHERE id=?",user(),id);}

    @Transactional public Map<String,Object> addSupport(String type,Map<String,Object>b){String table=supportTable(type);require(b,"entityType","Entity type is required.");require(b,"entityId","Entity id is required.");long id;String entity=text(b.get("entityType"),"LEAD");long entityId=longValue(b.get("entityId"));if("LEAD".equals(entity))assertLeadAccess(entityId);if("notes".equals(type)){require(b,"content","Note content is required.");id=insert("INSERT INTO lead_notes(organization_id,entity_type,entity_id,content,created_by,updated_by) VALUES(?,?,?,?,?,?)",ORGANIZATION_ID,entity,entityId,text(b.get("content"),""),user(),user());}else if("tasks".equals(type)){require(b,"title","Task title is required.");id=insert("INSERT INTO lead_tasks(organization_id,entity_type,entity_id,title,description,due_date,priority,assigned_user,status,reminder_at,created_by) VALUES(?,?,?,?,?,?,?,?,?,?,?)",ORGANIZATION_ID,entity,entityId,text(b.get("title"),""),text(b.get("description"),null),timestamp(b.get("dueDate")),text(b.get("priority"),"MEDIUM"),text(b.get("assignedUser"),user()),text(b.get("status"),"OPEN"),timestamp(b.get("reminderAt")),user());}else if("activities".equals(type)){require(b,"title","Activity title is required.");String activityType=text(b.get("activityType"),"NOTE").toUpperCase(Locale.ROOT);if("PROFILE".equals(activityType)&&(!(b.get("attachments") instanceof List<?> attachments)||attachments.isEmpty()))throw new IllegalArgumentException("Please attach at least one candidate profile.");boolean vendorProfile="PROFILE".equals(activityType)&&boolDefault(b.get("vendorProfile"),false);Long vendorId=vendorProfile?validateProfileVendor(b.get("vendorId")):null;id=insert("INSERT INTO lead_activities(organization_id,entity_type,entity_id,activity_type,title,description,vendor_profile,vendor_id,occurred_at,created_by) VALUES(?,?,?,?,?,?,?,?,?,?)",ORGANIZATION_ID,entity,entityId,activityType,text(b.get("title"),""),text(b.get("description"),null),vendorProfile,vendorId,timestampDefault(b.get("occurredAt")),user());addActivityAttachments(id,entity,entityId,b.get("attachments"));}else{require(b,"fileName","File name is required.");id=insert("INSERT INTO lead_attachments(organization_id,entity_type,entity_id,file_name,file_url,content_type,file_size,created_by) VALUES(?,?,?,?,?,?,?,?)",ORGANIZATION_ID,entity,entityId,text(b.get("fileName"),""),text(b.get("fileUrl"),""),text(b.get("contentType"),null),nullableLong(b.get("fileSize")),user());}if("LEAD".equals(entity)&&!"activities".equals(type))activity(entity,entityId,type.toUpperCase(Locale.ROOT),type.substring(0,1).toUpperCase(Locale.ROOT)+type.substring(1,type.length()-1)+" added","Created by "+user());return one("SELECT * FROM "+table+" WHERE id=?",List.of(id));}
    @Transactional public Map<String,Object> updateSupport(String type,long id,Map<String,Object>b){String table=supportTable(type);if("notes".equals(type)){require(b,"content","Note content is required.");Map<String,Object> note=oneRequired("SELECT entity_type entityType,entity_id entityId FROM lead_notes WHERE id=? AND organization_id=? AND deleted=FALSE",List.of(id,ORGANIZATION_ID),"Note not found.");jdbc.update("UPDATE lead_notes SET content=?,updated_by=? WHERE id=? AND organization_id=?",text(b.get("content"),""),user(),id,ORGANIZATION_ID);if("LEAD".equals(note.get("entityType")))activity("LEAD",number(note.get("entityId")),"NOTE_UPDATED","Note updated","Updated by "+user());}else if("tasks".equals(type)){require(b,"title","Task title is required.");jdbc.update("UPDATE lead_tasks SET title=?,description=?,due_date=?,priority=?,assigned_user=?,status=? WHERE id=? AND organization_id=?",text(b.get("title"),""),text(b.get("description"),null),timestamp(b.get("dueDate")),text(b.get("priority"),"MEDIUM"),text(b.get("assignedUser"),user()),text(b.get("status"),"OPEN"),id,ORGANIZATION_ID);}else if("activities".equals(type)){require(b,"title","Activity title is required.");Map<String,Object> existing=oneRequired("SELECT entity_type entityType,entity_id entityId FROM lead_activities WHERE id=? AND organization_id=?",List.of(id,ORGANIZATION_ID),"Activity not found.");long entityId=longValue(existing.get("entityId"));if("LEAD".equals(existing.get("entityType")))assertLeadAccess(entityId);String activityType=text(b.get("activityType"),"NOTE").toUpperCase(Locale.ROOT);boolean vendorProfile="PROFILE".equals(activityType)&&boolDefault(b.get("vendorProfile"),false);Long vendorId=vendorProfile?validateProfileVendor(b.get("vendorId")):null;jdbc.update("UPDATE lead_activities SET activity_type=?,title=?,description=?,vendor_profile=?,vendor_id=?,occurred_at=? WHERE id=? AND organization_id=?",activityType,text(b.get("title"),""),text(b.get("description"),null),vendorProfile,vendorId,timestampDefault(b.get("occurredAt")),id,ORGANIZATION_ID);if(b.get("removedAttachmentIds") instanceof List<?> removed)for(Object value:removed){Long attachmentId=nullableLong(value);if(attachmentId!=null)jdbc.update("UPDATE lead_attachments SET deleted=TRUE WHERE id=? AND activity_id=? AND organization_id=?",attachmentId,id,ORGANIZATION_ID);}addActivityAttachments(id,String.valueOf(existing.get("entityType")),entityId,b.get("attachments"));if("PROFILE".equals(activityType)){Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM lead_attachments WHERE activity_id=? AND organization_id=? AND deleted=FALSE",Integer.class,id,ORGANIZATION_ID);if(count==null||count==0)throw new IllegalArgumentException("Please attach at least one candidate profile.");}}else throw new IllegalArgumentException("This record type cannot be edited.");return one("SELECT * FROM "+table+" WHERE id=?",List.of(id));}

    private void addActivityAttachments(long activityId,String entity,long entityId,Object rawAttachments){if(!(rawAttachments instanceof List<?> attachments))return;for(Object raw:attachments){if(!(raw instanceof Map<?,?> file))continue;String name=text(file.get("fileName"),null),url=text(file.get("fileUrl"),null);if(!StringUtils.hasText(name)||!StringUtils.hasText(url))continue;insert("INSERT INTO lead_attachments(organization_id,entity_type,entity_id,activity_id,file_name,file_url,content_type,file_size,created_by) VALUES(?,?,?,?,?,?,?,?,?)",ORGANIZATION_ID,entity,entityId,activityId,name,url,text(file.get("contentType"),null),nullableLong(file.get("fileSize")),user());}}
    private Long validateProfileVendor(Object rawVendorId){Long vendorId=nullableLong(rawVendorId);if(vendorId==null)throw new IllegalArgumentException("Please select an IT Service Provider vendor.");Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM vendors WHERE id=? AND organization_id=? AND UPPER(status)='ACTIVE' AND UPPER(REPLACE(REPLACE(TRIM(vendor_type),'_',' '),'-',' '))='IT SERVICE PROVIDER'",Integer.class,vendorId,ORGANIZATION_ID);if(count==null||count==0)throw new IllegalArgumentException("Selected vendor must be an active IT Service Provider.");return vendorId;}
    @Transactional public void deleteSupport(String type,long id){String table=supportTable(type);Map<String,Object> record=one("SELECT entity_type entityType,entity_id entityId FROM "+table+" WHERE id=? AND organization_id=?",List.of(id,ORGANIZATION_ID));if("lead_activities".equals(table)||"lead_tasks".equals(table))jdbc.update("DELETE FROM "+table+" WHERE id=? AND organization_id=?",id,ORGANIZATION_ID);else jdbc.update("UPDATE "+table+" SET deleted=TRUE WHERE id=? AND organization_id=?",id,ORGANIZATION_ID);if(!record.isEmpty()&&"LEAD".equals(record.get("entityType")))activity("LEAD",number(record.get("entityId")),type.toUpperCase(Locale.ROOT)+"_DELETED",type.substring(0,1).toUpperCase(Locale.ROOT)+type.substring(1,type.length()-1)+" deleted","Deleted by "+user());}

    public String exportCsv(String entity,Map<String,String> filters){List<Map<String,Object>>rows=switch(entity){case"companies"->(List<Map<String,Object>>)companies(filters,0,10000).get("content");case"contacts"->(List<Map<String,Object>>)contacts(filters,0,10000).get("content");default->(List<Map<String,Object>>)leads(filters,0,10000).get("content");};if(rows.isEmpty())return "";StringBuilder out=new StringBuilder(String.join(",",rows.get(0).keySet())).append('\n');for(Map<String,Object>row:rows){for(int i=0;i<row.size();i++){if(i>0)out.append(',');Object value=new ArrayList<>(row.values()).get(i);out.append('"').append(String.valueOf(value==null?"":value).replace("\"","\"\"")).append('"');}out.append('\n');}return out.toString();}

    private String baseLeadSelect(){return "SELECT l.id,l.lead_number leadNumber,l.lead_name leadName,l.lead_type leadType,l.primary_skill_id primarySkillId,sm.skill_name primarySkillName,l.secondary_skill secondarySkill,l.type_details_json typeDetailsJson,l.status,l.source,l.owner_id ownerId,l.assigned_user_id assignedUserId,COALESCE(ar.party_name,au.name) assignedUserName,l.expected_value expectedValue,l.expected_close_date expectedCloseDate,l.probability,l.lead_score leadScore,l.next_step nextStep,l.priority,l.description,l.company_id companyId,c.company_name companyName,l.primary_contact_id primaryContactId,CONCAT(ct.first_name,' ',ct.last_name) contactName,ct.email contactEmail,ct.phone contactPhone,l.pipeline_id pipelineId,p.name pipelineName,l.stage_id stageId,s.name stageName,s.stage_type stageType,l.converted_deal_id convertedDealId,l.tags,l.draft,l.created_at createdAt,l.updated_at updatedAt FROM leads l LEFT JOIN skill_master sm ON sm.id=l.primary_skill_id LEFT JOIN lead_companies c ON c.id=l.company_id LEFT JOIN lead_contacts ct ON ct.id=l.primary_contact_id LEFT JOIN app_users au ON au.id=l.assigned_user_id LEFT JOIN business_records ar ON ar.id=au.resource_id JOIN lead_pipelines p ON p.id=l.pipeline_id JOIN pipeline_stages s ON s.id=l.stage_id ";}
    private String leadFilter(LocalDate from,LocalDate to,Long pipeline,String owner,String source,String status,List<Object>a,String alias){String w=access(alias)+" AND "+alias+".deleted=FALSE AND DATE("+alias+".created_at) BETWEEN ? AND ?";a.add(Date.valueOf(from));a.add(Date.valueOf(to));if(pipeline!=null){w+=" AND "+alias+".pipeline_id=?";a.add(pipeline);}if(StringUtils.hasText(owner)){w+=" AND "+alias+".owner_id=?";a.add(owner);}if(StringUtils.hasText(source)){w+=" AND "+alias+".source=?";a.add(source);}if(StringUtils.hasText(status)){w+=" AND UPPER("+alias+".status)=?";a.add(status.toUpperCase(Locale.ROOT));}return w;}
    private String access(String alias){
        String base=alias+".organization_id="+ORGANIZATION_ID;
        if(admin())return base;
        String creators=accessibleLoginSql();
        return base+" AND ("+alias+".created_by IN ("+creators+") OR "+alias+".assigned_user_id IN ("+accessibleUserIdSql()+") OR EXISTS(SELECT 1 FROM lead_user_share sh WHERE sh.lead_id="+alias+".id AND sh.user_id='"+sql(user())+"'))";
    }
    private String owned(String alias){return alias+".organization_id="+ORGANIZATION_ID+(admin()?"":" AND "+alias+".created_by IN ("+accessibleLoginSql()+")");}
    private String accessibleLoginSql(){
        var scope=dataScopes.getCurrentDataScope();
        if(scope.unrestricted())return "''";
        var values=appUsers.findLoginEmailsByIdIn(scope.accessibleUserIds()).stream().filter(Objects::nonNull).map(this::sql).map(value->"'"+value+"'").toList();
        return values.isEmpty()?"''":String.join(",",values);
    }
    private String accessibleUserIdSql(){var scope=dataScopes.getCurrentDataScope();if(scope.unrestricted())return "-1";var values=scope.accessibleUserIds().stream().map(String::valueOf).toList();return values.isEmpty()?"-1":String.join(",",values);}
    private String businessRecordAccess(String alias){return admin()?"1=1":alias+".created_by IN ("+accessibleUserIdSql()+")";}
    private String sql(String value){return value==null?"":value.replace("'","''");}
    private void assertLeadAccess(long id){Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM leads l WHERE l.id=? AND "+access("l")+" AND l.deleted=FALSE",Integer.class,id);if(count==null||count==0)throw new ResourceNotFoundException("Lead not found or not accessible.");}
    private void syncLeadContacts(long id,Map<String,Object>b){jdbc.update("DELETE FROM lead_contact_mapping WHERE lead_id=?",id);Long primary=nullableLong(b.get("primaryContactId"));if(primary!=null)jdbc.update("INSERT IGNORE INTO lead_contact_mapping(lead_id,contact_id,primary_contact) VALUES(?,?,TRUE)",id,primary);Object ids=b.get("contactIds");if(ids instanceof List<?> list)for(Object value:list){Long contact=nullableLong(value);if(contact!=null)jdbc.update("INSERT IGNORE INTO lead_contact_mapping(lead_id,contact_id,primary_contact) VALUES(?,?,?)",id,contact,Objects.equals(contact,primary));}}
    private void syncStages(long pipeline,List<?> stages){
        Map<Long,String> existing=jdbc.query("SELECT id,name FROM pipeline_stages WHERE pipeline_id=? AND active=TRUE",(rs,row)->Map.entry(rs.getLong("id"),rs.getString("name")),pipeline).stream().collect(Collectors.toMap(Map.Entry::getKey,Map.Entry::getValue));
        Set<Long> kept=new LinkedHashSet<>();
        int order=1;
        for(Object raw:stages){
            if(!(raw instanceof Map<?,?> s))throw new IllegalArgumentException("Invalid pipeline stage data.");
            String name=text(s.get("name"),null);
            if(!StringUtils.hasText(name))throw new IllegalArgumentException("Stage name is required.");
            Long id=nullableLong(s.get("id"));
            if(id==null)id=insert("INSERT INTO pipeline_stages(pipeline_id,name,probability,expected_duration_days,color,stage_type,sort_order) VALUES(?,?,?,?,?,?,?)",pipeline,name,number(s.get("probability")),number(s.get("expectedDurationDays")),text(s.get("color"),"#2563eb"),text(s.get("stageType"),"OPEN"),order);
            else{
                if(!existing.containsKey(id))throw new IllegalArgumentException("A pipeline stage does not belong to this pipeline.");
                if(!kept.add(id))throw new IllegalArgumentException("A pipeline stage was submitted more than once.");
                jdbc.update("UPDATE pipeline_stages SET name=?,probability=?,expected_duration_days=?,color=?,stage_type=?,sort_order=?,active=TRUE WHERE id=? AND pipeline_id=?",name,number(s.get("probability")),number(s.get("expectedDurationDays")),text(s.get("color"),"#2563eb"),text(s.get("stageType"),"OPEN"),order,id,pipeline);
            }
            kept.add(id);
            order++;
        }
        for(Map.Entry<Long,String> removed:existing.entrySet()){
            if(kept.contains(removed.getKey()))continue;
            Integer leads=jdbc.queryForObject("SELECT COUNT(*) FROM leads WHERE stage_id=? AND deleted=FALSE",Integer.class,removed.getKey());
            if(leads!=null&&leads>0)throw new ResourceConflictException("Stage '"+removed.getValue()+"' contains active leads and cannot be deleted. Move those leads to another stage first.");
            jdbc.update("UPDATE pipeline_stages SET active=FALSE WHERE id=? AND pipeline_id=?",removed.getKey(),pipeline);
        }
    }
    private void stageHistory(long lead,Integer from,long to,Integer oldP,int newP,String reason,String actor){jdbc.update("INSERT INTO lead_stage_history(lead_id,from_stage_id,to_stage_id,previous_probability,new_probability,reason,changed_by) VALUES(?,?,?,?,?,?,?)",lead,from,to,oldP,newP,reason,actor);}
    private void activity(String entity,long id,String type,String title,String description){jdbc.update("INSERT INTO lead_activities(organization_id,entity_type,entity_id,activity_type,title,description,created_by) VALUES(?,?,?,?,?,?,?)",ORGANIZATION_ID,entity,id,type,title,description,user());}
    private void audit(String entity,long id,String action,Map<String,Object>values){jdbc.update("INSERT INTO lead_audit_log(organization_id,entity_type,entity_id,action,new_values_json,performed_by) VALUES(?,?,?,?,?,?)",ORGANIZATION_ID,entity,id,action,json(values),user());}
    private List<Map<String,Object>> support(String table,String entity,long id){String deleted=("lead_notes".equals(table)||"lead_attachments".equals(table))?" AND deleted=FALSE":"";if("lead_activities".equals(table))return jdbc.queryForList("SELECT a.*,v.vendor_name vendorName FROM lead_activities a LEFT JOIN vendors v ON v.id=a.vendor_id WHERE a.organization_id=? AND a.entity_type=? AND a.entity_id=? ORDER BY a.occurred_at DESC",ORGANIZATION_ID,entity,id);return jdbc.queryForList("SELECT * FROM "+table+" WHERE organization_id=? AND entity_type=? AND entity_id=?"+deleted+" ORDER BY created_at DESC",ORGANIZATION_ID,entity,id);}
    private String supportTable(String type){return switch(type){case"notes"->"lead_notes";case"tasks"->"lead_tasks";case"activities"->"lead_activities";case"attachments"->"lead_attachments";default->throw new IllegalArgumentException("Unsupported record type.");};}
    private Map<String,Object> page(String sql,String countSql,List<Object>a,int page,int size){int safeSize=Math.min(Math.max(size,1),10000),safePage=Math.max(page,0);List<Object>pageArgs=new ArrayList<>(a);pageArgs.add(safeSize);pageArgs.add(safePage*safeSize);List<Map<String,Object>>content=jdbc.queryForList(sql+" LIMIT ? OFFSET ?",pageArgs.toArray());Long total=jdbc.queryForObject(countSql,Long.class,a.toArray());long t=total==null?0:total;return map("content",content,"totalElements",t,"totalPages",(int)Math.ceil(t/(double)safeSize),"number",safePage,"size",safeSize,"numberOfElements",content.size());}
    private List<Map<String,Object>> group(String sql,List<Object>a){return jdbc.queryForList(sql,a.toArray());}
    private Map<String,Object> one(String sql,List<Object>a){List<Map<String,Object>>r=jdbc.queryForList(sql,a.toArray());return r.isEmpty()?new LinkedHashMap<>():new LinkedHashMap<>(r.get(0));}
    private Map<String,Object> oneRequired(String sql,List<Object>a,String message){Map<String,Object>r=one(sql,a);if(r.isEmpty())throw new ResourceNotFoundException(message);return r;}
    private long insert(String sql,Object...args){GeneratedKeyHolder h=new GeneratedKeyHolder();jdbc.update(c->{PreparedStatement ps=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);for(int i=0;i<args.length;i++)ps.setObject(i+1,args[i]);return ps;},h);return Objects.requireNonNull(h.getKey()).longValue();}
    private Map<String,Object> changes(Map<String,Object>c,Map<String,Object>p){Map<String,Object>m=new LinkedHashMap<>();for(String k:List.of("totalLeads","newLeads","qualifiedLeads","convertedLeads","staffingLeads","fixedCostLeads","wonLeads","lostLeads","rejectedLeads","openLeads","proposalSent","pipelineValue","weightedPipeline")){double cv=number(c.get(k)),pv=number(p.get(k));m.put(k,pv==0?(cv==0?0:100):Math.round((cv-pv)*10000/pv)/100.0);}return m;}
    private double percent(double a,double b){return b==0?0:Math.round(a*10000/b)/100.0;}
    private String eq(String w,List<Object>a,String col,String v){if(StringUtils.hasText(v)){w+=" AND "+col+"=?";a.add(v.matches("\\d+")?Long.valueOf(v):v);}return w;}
    private String like(String w,List<Object>a,String col,String v){if(StringUtils.hasText(v)){w+=" AND LOWER("+col+") LIKE ?";a.add("%"+v.toLowerCase()+"%");}return w;}
    private String user(){var a=SecurityContextHolder.getContext().getAuthentication();return a==null?"system":a.getName();}
    private boolean admin(){return currentUsers.findCurrentUser().map(currentUsers::isAdmin).orElse(false);}
    private void require(Map<String,Object>b,String k,String m){if(!StringUtils.hasText(text(b.get(k),null)))throw new IllegalArgumentException(m);}
    private String text(Object v,String fallback){return v==null||!StringUtils.hasText(String.valueOf(v))?fallback:String.valueOf(v).trim();}
    private int number(Object v){if(v==null)return 0;if(v instanceof Number n)return n.intValue();try{return new BigDecimal(String.valueOf(v)).intValue();}catch(Exception e){return 0;}}
    private long longValue(Object v){if(v==null||!StringUtils.hasText(String.valueOf(v)))throw new IllegalArgumentException("Required identifier is missing.");return Long.parseLong(String.valueOf(v));}
    private Long nullableLong(Object v){if(v==null||!StringUtils.hasText(String.valueOf(v)))return null;return Long.valueOf(String.valueOf(v));}
    private BigDecimal decimal(Object v){if(v==null||!StringUtils.hasText(String.valueOf(v)))return BigDecimal.ZERO;return new BigDecimal(String.valueOf(v));}
    private Date date(Object v){return v==null||!StringUtils.hasText(String.valueOf(v))?null:Date.valueOf(String.valueOf(v));}
    private Timestamp timestamp(Object v){if(v==null||!StringUtils.hasText(String.valueOf(v)))return null;String value=String.valueOf(v).trim().replace('T',' ');if(value.length()==10)value+=" 00:00:00";else if(value.length()==16)value+=":00";return Timestamp.valueOf(value);}
    private Timestamp timestampDefault(Object v){Timestamp t=timestamp(v);return t==null?Timestamp.valueOf(LocalDateTime.now()):t;}
    private boolean bool(Object v){return Boolean.parseBoolean(String.valueOf(v));}private boolean boolDefault(Object v,boolean d){return v==null?d:bool(v);}
    private String json(Object v){try{return objectMapper.writeValueAsString(v==null?Map.of():v);}catch(JsonProcessingException e){return "{}";}}
    private Map<String,Object> map(Object...v){Map<String,Object>m=new LinkedHashMap<>();for(int i=0;i<v.length;i+=2)m.put(String.valueOf(v[i]),v[i+1]);return m;}
}
