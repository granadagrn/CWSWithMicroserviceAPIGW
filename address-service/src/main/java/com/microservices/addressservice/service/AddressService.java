package com.microservices.addressservice.service;

import com.microservices.addressservice.entity.Address;
import com.microservices.addressservice.exception.AddressAlreadyExistsException;
import com.microservices.addressservice.repository.AddressRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class AddressService {

    private final AddressRepository addressRepository;

    @Autowired
    public AddressService(AddressRepository addressRepository) {
        this.addressRepository = addressRepository;
    }

    public Address saveAddress(Address address) {
        Optional<Address> existingAddress = addressRepository.findByCustomerIdAndAddressName(
                address.getCustomerId(), address.getAddressName()
        );

        if (existingAddress.isPresent()) {
            throw new AddressAlreadyExistsException("Address with the name '"
                    + address.getAddressName() + "' already exists for customer ID: "
                    + address.getCustomerId());
        }

        return addressRepository.save(address);
    }

    public List<Address> getAddressesByCustomerId(Long customerId) {
        return addressRepository.findByCustomerId(customerId);
    }

    public List<Address> getAddresses() {
        return addressRepository.findAll();
    }

    public boolean deleteAddressesByCustomerId(Long customerId) {
        List<Address> addresses = addressRepository.findByCustomerId(customerId);

        if (addresses.isEmpty()) {
            return false;
        }

        addressRepository.deleteAll(addresses);
        return true;
    }
}
