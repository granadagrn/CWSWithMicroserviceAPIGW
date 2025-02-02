package com.microservices.addressservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "address")
@Data // Ensures getters and setters are automatically generated=> error
@NoArgsConstructor
@AllArgsConstructor
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "address_seq")
    @SequenceGenerator(name = "address_seq", sequenceName = "address_seq", allocationSize = 1)
    private Long id;

    //@NotBlank(message = "Address Name cannot be empty")
    @Column(nullable = false, unique = true)
    private String addressName;

    @Column(nullable = false)
    private String street;

    //@NotBlank(message = "City cannot be empty")
    @Column(nullable = false)
    private String city;

    //@NotBlank(message = "State cannot be empty")
    @Column(nullable = false)
    private String state;

    @Column(nullable = false)
    private String zipCode;

    @Column(name = "customer_id")
    private Long customerId; // Store customer ID instead of direct relation
}
