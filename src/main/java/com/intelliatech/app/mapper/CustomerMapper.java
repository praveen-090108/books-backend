package com.intelliatech.app.mapper;

import com.intelliatech.app.dto.request.CustomerRequest;
import com.intelliatech.app.dto.response.CustomerResponse;
import com.intelliatech.app.entity.Customer;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface CustomerMapper {

    Customer toEntity(CustomerRequest request);

    CustomerResponse toResponse(Customer customer);

    void updateEntity(CustomerRequest request, @MappingTarget Customer customer);
}
