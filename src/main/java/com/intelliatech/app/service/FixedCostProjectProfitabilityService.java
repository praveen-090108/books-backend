package com.intelliatech.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intelliatech.app.entity.BusinessRecord;
import com.intelliatech.app.repository.BusinessRecordRepository;
import com.intelliatech.app.security.DataScopeService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FixedCostProjectProfitabilityService {
    private static final int SCALE = 10;
    private final JdbcTemplate jdbc;
    private final BusinessRecordRepository records;
    private final DataScopeService scopes;
    private final ObjectMapper json;

    public record ResourceCost(Long assignmentId, Long employeeId, String employeeName,
            BigDecimal monthlySalary, String salaryCurrency, LocalDate activeFrom,
            LocalDate activeTo, int activeDays, int daysInMonth, BigDecimal proration,
            BigDecimal originalCost, BigDecimal exchangeRate, BigDecimal convertedCost,
            BigDecimal inrExchangeRate, BigDecimal costInInr) {}
    public record MonthResult(String month, String label, BigDecimal plannedValue,
            BigDecimal resourceCost, BigDecimal otherCost, BigDecimal totalCost,
            BigDecimal profit, BigDecimal profitPercentage, BigDecimal plannedValueInInr,
            BigDecimal resourceCostInInr, BigDecimal totalCostInInr, BigDecimal profitInInr,
            List<ResourceCost> resources) {}
    public record Totals(BigDecimal plannedValue, BigDecimal resourceCost,
            BigDecimal otherCost, BigDecimal totalCost, BigDecimal profit,
            BigDecimal profitPercentage, BigDecimal plannedValueInInr,
            BigDecimal resourceCostInInr, BigDecimal totalCostInInr, BigDecimal profitInInr) {}
    public record Result(Long projectId, String currencyCode, BigDecimal contractValue,
            List<MonthResult> months, Totals totals) {}

    private record Assignment(Long id, Long employeeId, String employeeName,
            BigDecimal salary, String salaryCurrency, LocalDate addedDate, LocalDate removedDate) {}

    @Transactional(readOnly = true)
    public Result calculate(Long projectId) {
        BusinessRecord project = records.findByModuleAndTypeAndId("projects", "fixedCost", projectId)
                .orElseThrow(() -> new IllegalArgumentException("Fixed Cost Project does not exist."));
        scopes.validateRecordAccess(project.getCreatedBy());
        LocalDate start = project.getRecordDate();
        LocalDate end = project.getDueDate() == null ? start : project.getDueDate();
        if (end.isBefore(start)) throw new IllegalArgumentException("Project End Date must be on or after Start Date.");
        String projectCurrency = projectCurrency(project);
        BigDecimal contract = amount(project.getAmount());
        List<Assignment> assignments = assignments(projectId);
        List<YearMonth> periods = periods(start, end);
        BigDecimal totalWeight = periods.stream().map(month -> projectWeight(month, start, end))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<MonthResult> months = new ArrayList<>();
        BigDecimal plannedAllocated = BigDecimal.ZERO;
        for (int index = 0; index < periods.size(); index++) {
            YearMonth period = periods.get(index);
            BigDecimal planned = index == periods.size() - 1
                    ? contract.subtract(plannedAllocated)
                    : contract.multiply(projectWeight(period, start, end)).divide(totalWeight, SCALE, RoundingMode.HALF_UP);
            plannedAllocated = plannedAllocated.add(planned);
            List<ResourceCost> details = resourceCosts(period, start, end, projectCurrency, assignments);
            BigDecimal resource = details.stream().map(ResourceCost::convertedCost).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal resourceInInr = details.stream().map(ResourceCost::costInInr).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal other = BigDecimal.ZERO;
            BigDecimal total = resource.add(other);
            BigDecimal profit = planned.subtract(total);
            BigDecimal plannedInInr = planned.multiply(exchangeRate(projectCurrency, "INR", period.atEndOfMonth().isAfter(end) ? end : period.atEndOfMonth()));
            BigDecimal totalInInr = resourceInInr;
            BigDecimal profitInInr = plannedInInr.subtract(totalInInr);
            months.add(new MonthResult(period.toString(), period.format(DateTimeFormatter.ofPattern("MMM yyyy")),
                    money(planned), money(resource), money(other), money(total), money(profit), percentage(profitInInr, plannedInInr),
                    money(plannedInInr), money(resourceInInr), money(totalInInr), money(profitInInr), details));
        }
        BigDecimal resourceTotal = months.stream().map(MonthResult::resourceCost).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal otherTotal = months.stream().map(MonthResult::otherCost).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCost = resourceTotal.add(otherTotal);
        BigDecimal profit = contract.subtract(totalCost);
        BigDecimal plannedInInr = months.stream().map(MonthResult::plannedValueInInr).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal resourceInInr = months.stream().map(MonthResult::resourceCostInInr).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalInInr = months.stream().map(MonthResult::totalCostInInr).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal profitInInr = months.stream().map(MonthResult::profitInInr).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Result(projectId, projectCurrency, money(contract), months,
                new Totals(money(contract), money(resourceTotal), money(otherTotal), money(totalCost), money(profit), percentage(profitInInr, plannedInInr),
                        money(plannedInInr), money(resourceInInr), money(totalInInr), money(profitInInr)));
    }

    public MonthResult month(Long projectId, String yearMonth) {
        return calculate(projectId).months().stream().filter(row -> row.month().equals(yearMonth)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("The requested month is outside the Project duration."));
    }

    private List<ResourceCost> resourceCosts(YearMonth month, LocalDate projectStart, LocalDate projectEnd,
            String projectCurrency, List<Assignment> assignments) {
        LocalDate monthStart = month.atDay(1);
        LocalDate monthEnd = month.atEndOfMonth();
        List<ResourceCost> result = new ArrayList<>();
        for (Assignment assignment : assignments) {
            LocalDate from = latest(projectStart, monthStart, assignment.addedDate());
            LocalDate assignmentEnd = assignment.removedDate() == null ? projectEnd : assignment.removedDate();
            LocalDate to = earliest(projectEnd, monthEnd, assignmentEnd);
            if (to.isBefore(from)) continue;
            int activeDays = (int) (to.toEpochDay() - from.toEpochDay() + 1);
            int daysInMonth = month.lengthOfMonth();
            BigDecimal proration = BigDecimal.valueOf(activeDays).divide(BigDecimal.valueOf(daysInMonth), SCALE, RoundingMode.HALF_UP);
            BigDecimal original = assignment.salary().multiply(proration);
            BigDecimal rate = exchangeRate(assignment.salaryCurrency(), projectCurrency, to);
            BigDecimal converted = original.multiply(rate);
            BigDecimal inrRate = exchangeRate(assignment.salaryCurrency(), "INR", to);
            BigDecimal costInInr = original.multiply(inrRate);
            result.add(new ResourceCost(assignment.id(), assignment.employeeId(), assignment.employeeName(),
                    money(assignment.salary()), assignment.salaryCurrency(), from, to, activeDays, daysInMonth,
                    proration.setScale(4, RoundingMode.HALF_UP), money(original), rate.setScale(6, RoundingMode.HALF_UP), money(converted),
                    inrRate.setScale(6, RoundingMode.HALF_UP), money(costInInr)));
        }
        return result;
    }

    private List<Assignment> assignments(Long projectId) {
        String sql = "SELECT t.id,t.employee_id,e.party_name," +
                "COALESCE(CAST(JSON_UNQUOTE(JSON_EXTRACT(e.notes,'$.monthlySalary')) AS DECIMAL(14,2)),0) salary," +
                "COALESCE(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(e.notes,'$.salaryCurrency')),'null'),'INR') salary_currency," +
                "t.added_date,t.removed_date FROM fixed_cost_project_team_member t JOIN business_records e ON e.id=t.employee_id " +
                "WHERE t.project_id=? AND t.assignment_status='ACTIVE' AND LOWER(e.status)='active' ORDER BY t.added_date,t.id";
        return jdbc.query(sql, (rs, row) -> new Assignment(rs.getLong("id"), rs.getLong("employee_id"),
                rs.getString("party_name"), amount(rs.getBigDecimal("salary")), rs.getString("salary_currency"),
                rs.getObject("added_date", LocalDate.class), rs.getObject("removed_date", LocalDate.class)), projectId);
    }

    private BigDecimal exchangeRate(String from, String to, LocalDate date) {
        if (from.equalsIgnoreCase(to)) return BigDecimal.ONE;
        BigDecimal direct = lookupRate(from, to, date);
        if (direct != null) return direct;
        BigDecimal inverse = lookupRate(to, from, date);
        if (inverse != null) return BigDecimal.ONE.divide(inverse, SCALE, RoundingMode.HALF_UP);
        if (!"INR".equalsIgnoreCase(from) && !"INR".equalsIgnoreCase(to)) {
            return exchangeRate(from, "INR", date).multiply(exchangeRate("INR", to, date));
        }
        throw new IllegalArgumentException("No active exchange rate is configured for " + from + " to " + to + " on " + date + ".");
    }

    private BigDecimal lookupRate(String from, String to, LocalDate date) {
        List<BigDecimal> rates = jdbc.query("SELECT rate FROM exchange_rate_master WHERE from_currency=? AND to_currency=? AND active=TRUE AND effective_date<=? ORDER BY effective_date DESC,id DESC LIMIT 1",
                (rs, row) -> rs.getBigDecimal(1), from.toUpperCase(), to.toUpperCase(), date);
        return rates.isEmpty() ? null : rates.get(0);
    }

    private String projectCurrency(BusinessRecord project) {
        try {
            JsonNode notes = json.readTree(project.getNotes() == null ? "{}" : project.getNotes());
            String code = notes.path("currencyCode").asText(project.getPaymentMode());
            return code == null || code.isBlank() ? "INR" : code.trim().substring(0, Math.min(3, code.trim().length())).toUpperCase();
        } catch (Exception ignored) { return "INR"; }
    }
    private List<YearMonth> periods(LocalDate start, LocalDate end) { List<YearMonth> result = new ArrayList<>(); for (YearMonth cursor=YearMonth.from(start), last=YearMonth.from(end); !cursor.isAfter(last); cursor=cursor.plusMonths(1)) result.add(cursor); return result; }
    private BigDecimal projectWeight(YearMonth month, LocalDate start, LocalDate end) { LocalDate from=latest(month.atDay(1),start); LocalDate to=earliest(month.atEndOfMonth(),end); long days=to.toEpochDay()-from.toEpochDay()+1; return BigDecimal.valueOf(days).divide(BigDecimal.valueOf(month.lengthOfMonth()),SCALE,RoundingMode.HALF_UP); }
    private LocalDate latest(LocalDate... dates) { LocalDate result=dates[0]; for(LocalDate date:dates)if(date!=null&&date.isAfter(result))result=date; return result; }
    private LocalDate earliest(LocalDate... dates) { LocalDate result=dates[0]; for(LocalDate date:dates)if(date!=null&&date.isBefore(result))result=date; return result; }
    private BigDecimal amount(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }
    private BigDecimal percentage(BigDecimal value, BigDecimal base) { return base.signum()==0?BigDecimal.ZERO:value.multiply(BigDecimal.valueOf(100)).divide(base,4,RoundingMode.HALF_UP); }
}
