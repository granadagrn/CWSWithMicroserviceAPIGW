package com.microservices.customerservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddressDTO {
    private Long id;
    private String addressName;
    private String street;
    private String city;
    private String state;
    private String zipCode;

    private Long customerId;

}
