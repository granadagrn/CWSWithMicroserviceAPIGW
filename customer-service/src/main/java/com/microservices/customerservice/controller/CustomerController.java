package com.microservices.customerservice.controller;

import com.microservices.customerservice.dto.AddressDTO;
import com.microservices.customerservice.entity.Customer;
import com.microservices.customerservice.service.CustomerService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/customers")
@Validated
public class CustomerController {
    //private static final Logger logger = LoggerFactory.getLogger(CustomerService.class);


    private final CustomerService customerService;
    private final RestTemplate restTemplate;

    //@Value("${address-service.service.url}")api-gateway.service.url
    //private String addressServiceUrl;

    @Value("${api-gateway.service.url}")
    private String apiGatewayServiceUrl;

    @Autowired
    public CustomerController(CustomerService customerService, RestTemplate restTemplate) {
        this.customerService = customerService;
        this.restTemplate = restTemplate;
    }

    @GetMapping()
    public List<Customer> getCustomers() {
        //logger.info("getCustomers method starts in CustomerController");
        List<Customer> customers = customerService.getAllCustomers();
        //logger.info("getCustomers collects all customers");
        customers.forEach(customer -> {
            //logger.info("getCustomers method checking for addresses in address-service");
            List<AddressDTO> addresses = fetchCustomerAddresses(customer.getId());
            customer.setAddresses(addresses);
        });
        //logger.info("getCustomers method returns the customers information with addresses");
        return customers;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteCustomerByCustomerId (@PathVariable Long id) {
        // Check if customer exists
        Customer existingCustomer = customerService.getCustomerById(id);
        if (existingCustomer == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Customer not found!");
        }

        // Delete customer addresses via address-service
        boolean addressDeletionSuccess = deleteCustomerAddresses(id);
        if (!addressDeletionSuccess) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to delete customer addresses from address-service.");
        }

        // Delete customer from customer-service database
        customerService.deleteCustomerById(id);

        return ResponseEntity.ok("Customer and related addresses deleted successfully.");
    }

    private List<AddressDTO> fetchCustomerAddresses(Long customerId) {
        //logger.info("fetchCustomerAddress method starts in CustomerController");
        String tempServiceUrl = apiGatewayServiceUrl + "/addresses/customer/" + customerId;
        //logger.info("The address-service url related with customerId is: " + tempServiceUrl);
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + extractBearerToken()); // Add token to headers

        HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<List<AddressDTO>> response = restTemplate.exchange(
                tempServiceUrl,
                HttpMethod.GET,
                entity,
                new ParameterizedTypeReference<>() {}
        );

        return response.getBody();
    }

    private String extractBearerToken() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtToken) {
            return jwtToken.getToken().getTokenValue();
        }
        return null;
    }

    // GET http://localhost:8082/customers/<customerID>
    @GetMapping("/{id}")
    public ResponseEntity<?> getCustomerById(@PathVariable Long id) {
        Customer customer = customerService.getCustomerById(id);
        if (customer == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Customer not found!");
        }

        // Fetch addresses from address-service
        List<AddressDTO> addresses = fetchCustomerAddresses(id);
        customer.setAddresses(addresses);

        return ResponseEntity.ok(customer);
    }

    @GetMapping("/{customerId}/addresses")
    public List<AddressDTO> getCustomerAddresses(@PathVariable Long customerId) {
        String tempServiceUrl = apiGatewayServiceUrl + "/customer/" + customerId;
        return restTemplate.getForObject(tempServiceUrl, List.class);
    }

    /***@PostMapping
    public ResponseEntity<String> addCustomer(@Valid @RequestBody Customer customer) {
        customerService.addCustomer(customer);
        return new ResponseEntity<>("Customer added successfully", HttpStatus.CREATED);
    }**/

    // 1. Check if the email exist, if not continue
    // 2. Save the customer first to generate a customerID
    // 3. If addresses are provided, send them to address-service with the generated customerID
    // 4. Return the saved customer data.
    // POST http://localhost:8082/customers with related Body
    @PostMapping
    public ResponseEntity<?> createCustomer(@Valid @RequestBody Customer customer) {
        if (customerService.existsByEmail(customer.getEmail())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Email already exists.");
        }

        // Save customer first and get the generated ID
        Customer savedCustomer = customerService.addCustomer(customer);

        // If customer has addresses, send them to address-service
        if (customer.getAddresses() != null && !customer.getAddresses().isEmpty()) {
            for (AddressDTO address : customer.getAddresses()) {
                address.setCustomerId(savedCustomer.getId());  // Set customer ID to each address
                sendAddressToAddressService(address);
            }
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(savedCustomer);
    }

    private void sendAddressToAddressService(AddressDTO address) {
        String tempUrl = apiGatewayServiceUrl + "/addresses";
        try {
            restTemplate.postForObject(tempUrl, address, AddressDTO.class);
            System.out.println("Address sent successfully: " + address);
        } catch (HttpServerErrorException e) {
            System.err.println("Error from address-service: " + e.getResponseBodyAsString());
        } catch (Exception e) {
            System.err.println("Unexpected error while sending address: " + e.getMessage());
        }
    }

    // POST http://localhost:8082/customers/<customerID> with related body
    @PutMapping("/{id}")
    public ResponseEntity<?> updateCustomer(@PathVariable Long id, @Valid @RequestBody Customer customerRequest) {
        Customer existingCustomer = customerService.getCustomerById(id);

        // Step 1: Verify the customer exists
        if (existingCustomer == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Customer not found!");
        }

        // Step 2: Update customer details (excluding email)
        if (!existingCustomer.getEmail().equals(customerRequest.getEmail())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Email cannot be updated!");
        }
        existingCustomer.setName(customerRequest.getName());
        existingCustomer.setPhoneNumber(customerRequest.getPhoneNumber());

        // Save the updated customer
        Customer updatedCustomer = customerService.updateCustomer(existingCustomer);

        // Step 3: Update addresses
        if (customerRequest.getAddresses() != null) {
            handleAddressUpdates(id, customerRequest.getAddresses());
        }

        return ResponseEntity.ok(updatedCustomer);
    }

    private void handleAddressUpdates(Long customerId, List<AddressDTO> updatedAddresses) {
        // Fetch existing addresses for the customer from address-service
        String url = apiGatewayServiceUrl + "/customer/" + customerId;
        ResponseEntity<AddressDTO[]> response = restTemplate.getForEntity(url, AddressDTO[].class);
        List<AddressDTO> existingAddresses = Arrays.asList(response.getBody());

        // Handle deletions, additions, and updates
        deleteRemovedAddresses(existingAddresses, updatedAddresses);
        addNewAddresses(customerId, updatedAddresses, existingAddresses);
        updateModifiedAddresses(updatedAddresses, existingAddresses);
    }

    private void deleteRemovedAddresses(List<AddressDTO> existingAddresses, List<AddressDTO> updatedAddresses) {
        List<Long> updatedIds = updatedAddresses.stream()
                .map(AddressDTO::getId)
                .collect(Collectors.toList());

        for (AddressDTO existingAddress : existingAddresses) {
            if (!updatedIds.contains(existingAddress.getId())) {
                String deleteUrl = apiGatewayServiceUrl + "/" + existingAddress.getId();
                restTemplate.delete(deleteUrl);
            }
        }
    }

    private void addNewAddresses(Long customerId, List<AddressDTO> updatedAddresses, List<AddressDTO> existingAddresses) {
        List<String> existingAddressNames = existingAddresses.stream()
                .map(AddressDTO::getAddressName)
                .collect(Collectors.toList());

        for (AddressDTO updatedAddress : updatedAddresses) {
            if (updatedAddress.getId() == null && !existingAddressNames.contains(updatedAddress.getAddressName())) {
                updatedAddress.setCustomerId(customerId);
                restTemplate.postForObject(apiGatewayServiceUrl, updatedAddress, AddressDTO.class);
            }
        }
    }

    private void updateModifiedAddresses(List<AddressDTO> updatedAddresses, List<AddressDTO> existingAddresses) {
        for (AddressDTO updatedAddress : updatedAddresses) {
            for (AddressDTO existingAddress : existingAddresses) {
                if (updatedAddress.getId() != null &&
                        updatedAddress.getId().equals(existingAddress.getId()) &&
                        !updatedAddress.equals(existingAddress)) {
                    String updateUrl = apiGatewayServiceUrl + "/" + existingAddress.getId();
                    restTemplate.put(updateUrl, updatedAddress);
                }
            }
        }
    }

    private boolean deleteCustomerAddresses(Long customerId) {
        String tempURL = apiGatewayServiceUrl + "/addresses/customer/" + customerId;

        try {
            ResponseEntity<Void> response = restTemplate.exchange(
                    tempURL,  // URL
                    HttpMethod.DELETE,  // HTTP Method
                    null,               // requestEntity
                    Void.class          // ResponseType
            );
            return true; // Addresses deleted successfully
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return true;  // No addresses found, consider it successful
            }
            System.err.println("Error deleting customer addresses: " + e.getMessage());
            return false;
        } catch (Exception e) {
            System.err.println("Service unreachable or other error: " + e.getMessage());
            return false;
        }
    }

}
