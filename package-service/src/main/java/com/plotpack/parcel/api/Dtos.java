package com.plotpack.parcel.api;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class Dtos {
    private Dtos() {
    }

    /** Sent by the floor executive's tablet for each purchased item. */
    public record CreatePackageRequest(
            @NotNull Long customerId,
            @Min(1) @Max(3) int floor,
            @NotBlank @Size(max = 200) String description,
            @NotNull @DecimalMin("1.00") @Digits(integer = 10, fraction = 2) BigDecimal amount) {
    }

    public record PackageResponse(Long packageId, String token, String binCode, int binPackageCount, int binCapacity,
                                  boolean newBin, int floor, String description, BigDecimal amount,
                                  int tokenPackageCount, BigDecimal estimatedBill, Instant createdAt) {
    }

    /** What the ground-floor team sees after the customer tells their e-token. */
    public record TokenView(String token, String status, Long customerId, String customerName, String customerPhone,
                            int packageCount, BigDecimal estimatedBill, List<BinItems> bins) {
    }

    public record BinItems(String binCode, List<Item> items) {
    }

    public record Item(Long packageId, int floor, String description, BigDecimal amount, String status) {
    }

    public record DeliveryResult(String token, String customerName, int deliveredCount, BigDecimal billTotal,
                                 List<String> binsVisited, List<String> binsReleased) {
    }

    public record GridCell(Long binId, String code, String rack, int row, int col, int capacity, int packageCount,
                           String customerName, String token) {
    }
}
