package com.intelliatech.app.service;

import com.intelliatech.app.dto.request.CustomerRequest;
import com.intelliatech.app.dto.response.CustomerResponse;

import java.util.List;

public interface CustomerService {

    List<CustomerResponse> findAll();

    CustomerResponse findById(Long id);

    CustomerResponse create(CustomerRequest request);

    CustomerResponse update(Long id, CustomerRequest request);

    void delete(Long id);
}
