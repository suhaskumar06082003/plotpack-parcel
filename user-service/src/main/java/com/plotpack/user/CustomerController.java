package com.plotpack.user;

import com.plotpack.common.web.BusinessException;
import com.plotpack.common.web.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Customers", description = "Called by the floor executive before creating a package")
public class CustomerController {

    public record RegisterRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Pattern(regexp = "[0-9]{10}", message = "must be a 10-digit mobile number") String phone) {
    }

    public record CustomerResponse(Long id, String name, String phone, Instant createdAt) {
        static CustomerResponse of(Customer c) {
            return new CustomerResponse(c.getId(), c.getName(), c.getPhone(), c.getCreatedAt());
        }
    }

    private final CustomerRepository repository;

    public CustomerController(CustomerRepository repository) {
        this.repository = repository;
    }

    @PostMapping
    @Operation(summary = "Find-or-register a customer by phone (safe to call repeatedly)")
    public ResponseEntity<CustomerResponse> register(@Valid @RequestBody RegisterRequest req) {
        var existing = repository.findByPhone(req.phone());
        if (existing.isPresent()) {
            return ResponseEntity.ok(CustomerResponse.of(existing.get()));
        }
        try {
            Customer saved = repository.saveAndFlush(new Customer(req.name().trim(), req.phone()));
            return ResponseEntity.status(HttpStatus.CREATED).body(CustomerResponse.of(saved));
        } catch (DataIntegrityViolationException race) {
            // Two floors registered the same phone at once; the unique key picked a winner.
            return ResponseEntity.ok(CustomerResponse.of(repository.findByPhone(req.phone()).orElseThrow()));
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a customer by id (used by package-service)")
    public CustomerResponse get(@PathVariable Long id) {
        return repository.findById(id).map(CustomerResponse::of)
                .orElseThrow(() -> new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND, "Customer " + id + " not found"));
    }

    @GetMapping("/lookup")
    @Operation(summary = "Find a customer by phone")
    public CustomerResponse byPhone(@RequestParam String phone) {
        return repository.findByPhone(phone).map(CustomerResponse::of)
                .orElseThrow(() -> new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND, "No customer with phone " + phone));
    }
}
