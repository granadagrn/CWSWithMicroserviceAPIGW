package com.microservices.addressservice.repository;

import com.microservices.addressservice.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AddressRepository extends JpaRepository<Address, Long> {
    List<Address> findByCustomerId(Long customerId);

    @Query("SELECT a FROM Address a WHERE a.customerId = :customerId AND a.addressName = :addressName")
    Optional<Address> findByCustomerIdAndAddressName(Long customerId, String addressName);
}
