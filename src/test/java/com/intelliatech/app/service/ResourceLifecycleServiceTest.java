package com.intelliatech.app.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intelliatech.app.entity.BusinessRecord;
import com.intelliatech.app.repository.BusinessRecordRepository;
import com.intelliatech.app.security.CurrentUserService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class ResourceLifecycleServiceTest {
    @Mock JdbcTemplate jdbc;
    @Mock BusinessRecordRepository records;
    @Mock CurrentUserService users;
    private ResourceLifecycleService service;
    private BusinessRecord resource;

    @BeforeEach
    void setUp() {
        service = new ResourceLifecycleService(jdbc, records, users, new ObjectMapper());
        resource = new BusinessRecord();
        resource.setNotes("{\"monthlySalary\":50000,\"salaryCurrency\":\"INR\"}");
    }

    @Test
    void genericResourceEditMayKeepCurrentSalary() {
        assertDoesNotThrow(() -> service.assertSalaryUnchanged(resource,
                "{\"monthlySalary\":50000,\"salaryCurrency\":\"INR\"}"));
    }

    @Test
    void genericResourceEditCannotBypassSalaryHistory() {
        assertThatThrownBy(() -> service.assertSalaryUnchanged(resource,
                "{\"monthlySalary\":60000,\"salaryCurrency\":\"INR\"}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Salary Revision");
    }

    @Test
    void staffingAllocationUsesCanonicalSelectedResource() {
        BusinessRecord project = new BusinessRecord();
        project.setId(91L);
        project.setModule("projects");
        project.setType("staffing");
        project.setNotes("{\"selectedResource\":\"Hardeep Singh\",\"workingResource\":\"Sumit\"}");
        when(jdbc.queryForList(anyString(), eq(Long.class), eq("Hardeep Singh"))).thenReturn(List.of());

        service.synchronizeStaffingAllocation(project);

        verify(jdbc).queryForList(anyString(), eq(Long.class), eq("Hardeep Singh"));
    }
}
