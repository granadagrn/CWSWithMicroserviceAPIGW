package com.microservices.addressservice.controller;

import com.microservices.addressservice.entity.Address;
import com.microservices.addressservice.service.AddressService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/addresses")
public class AddressController {

    private final AddressService addressService;

    @Autowired
    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    @PostMapping
    public ResponseEntity<?> addAddress(@RequestBody Address address) {
        try {
            Address savedAddress = addressService.saveAddress(address);
            return ResponseEntity.status(HttpStatus.CREATED).body(savedAddress);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error saving address: " + e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<List<Address>> getAddresses() {
        return ResponseEntity.ok(addressService.getAddresses());
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<Address>> getAddressesByCustomer(@PathVariable Long customerId) {
        return ResponseEntity.ok(addressService.getAddressesByCustomerId(customerId));
    }

    @DeleteMapping("/customer/{customerId}")
    public ResponseEntity<String> deleteAddressesByCustomerId(@PathVariable Long customerId) {
        boolean isDeleted = addressService.deleteAddressesByCustomerId(customerId);
        if (isDeleted) {
            return ResponseEntity.ok("Addresses deleted successfully.");
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No addresses found for customerId: " + customerId);
        }
    }
}
