package com.intelliatech.app.service.impl;

import com.intelliatech.app.dto.request.CustomerRequest;
import com.intelliatech.app.dto.response.CustomerResponse;
import com.intelliatech.app.entity.Customer;
import com.intelliatech.app.exception.DuplicateResourceException;
import com.intelliatech.app.exception.ResourceNotFoundException;
import com.intelliatech.app.mapper.CustomerMapper;
import com.intelliatech.app.repository.CustomerRepository;
import com.intelliatech.app.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerMapper customerMapper;

    @Override
    @Transactional(readOnly = true)
    public List<CustomerResponse> findAll() {
        return customerRepository.findAll()
                .stream()
                .map(customerMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerResponse findById(Long id) {
        return customerMapper.toResponse(getCustomer(id));
    }

    @Override
    public CustomerResponse create(CustomerRequest request) {
        if (customerRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("Customer email already exists");
        }
        Customer customer = customerMapper.toEntity(request);
        return customerMapper.toResponse(customerRepository.save(customer));
    }

    @Override
    public CustomerResponse update(Long id, CustomerRequest request) {
        Customer customer = getCustomer(id);
        customerMapper.updateEntity(request, customer);
        return customerMapper.toResponse(customer);
    }

    @Override
    public void delete(Long id) {
        customerRepository.delete(getCustomer(id));
    }

    private Customer getCustomer(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
    }
}
