package com.plotpack.parcel.service;

import com.plotpack.common.events.EventTypes;
import com.plotpack.common.events.PlotPackEvent;
import com.plotpack.common.events.Topics;
import com.plotpack.common.outbox.OutboxWriter;
import com.plotpack.parcel.api.Dtos.CreatePackageRequest;
import com.plotpack.parcel.api.Dtos.PackageResponse;
import com.plotpack.parcel.client.UserClient.CustomerView;
import com.plotpack.parcel.domain.BinAllocation;
import com.plotpack.parcel.domain.BinAllocationRepository;
import com.plotpack.parcel.domain.Parcel;
import com.plotpack.parcel.domain.ParcelRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Saves the package in ONE transaction:
 * lock the bin's allocation row (PESSIMISTIC_WRITE) -> check space -> +1 -> e-token -> insert package -> outbox event.
 *
 * <p>The row lock stops two floors adding to the same bin at the same moment from both reading
 * count=3, both writing 4, and squeezing a 5th package into a 4-slot bin.
 */
@Component
public class PackageWriter {
    private static final String TOKEN_CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    /** The reserved bin filled up or was released before we committed; pick again. */
    public static class StaleReservationException extends RuntimeException {
        public StaleReservationException(String binCode) {
            super("Bin " + binCode + " no longer has space");
        }
    }

    private final BinAllocationRepository allocations;
    private final ParcelRepository parcels;
    private final OutboxWriter outbox;

    public PackageWriter(BinAllocationRepository allocations, ParcelRepository parcels, OutboxWriter outbox) {
        this.allocations = allocations;
        this.parcels = parcels;
        this.outbox = outbox;
    }

    @Transactional
    public PackageResponse commit(AllocationService.Reservation reservation, CreatePackageRequest req,
                                  CustomerView customer, String idempotencyKey, String requestHash) {
        BinAllocation alloc = allocations.findByIdForUpdate(reservation.allocationId())
                .orElseThrow(() -> new StaleReservationException(reservation.binCode()));
        if (!alloc.hasSpace()) {
            throw new StaleReservationException(alloc.getBinCode());
        }
        alloc.addPackage();

        // One e-token per shopping visit: reuse the customer's token while they still have packages waiting.
        String token = parcels.findFirstByCustomerIdAndStatus(customer.id(), Parcel.Status.ALLOCATED)
                .map(Parcel::getToken)
                .orElseGet(this::newToken);

        // UNIQUE(idempotency_key): a concurrent duplicate fails here and everything above rolls back.
        Parcel parcel = parcels.saveAndFlush(new Parcel(token, customer.id(), customer.name(), customer.phone(), alloc,
                req.floor(), req.description(), req.amount(), idempotencyKey, requestHash));

        PackageResponse response = describe(parcel, reservation.newBin());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("packageId", parcel.getId());
        payload.put("token", token);
        payload.put("customerName", customer.name());
        payload.put("customerPhone", customer.phone());
        payload.put("binCode", alloc.getBinCode());
        payload.put("floor", req.floor());
        payload.put("description", req.description());
        payload.put("amount", req.amount());
        payload.put("tokenPackageCount", response.tokenPackageCount());
        payload.put("estimatedBill", response.estimatedBill());
        outbox.append(Topics.PACKAGE_EVENTS, token,
                PlotPackEvent.of(EventTypes.PACKAGE_ALLOCATED, String.valueOf(parcel.getId()), payload));
        return response;
    }

    /** Builds the API response, including the running estimated bill for the e-token. */
    public PackageResponse describe(Parcel p, boolean newBin) {
        BinAllocation alloc = allocations.findById(p.getAllocationId()).orElseThrow();
        List<Parcel> sameToken = parcels.findByTokenOrderByBinCodeAscIdAsc(p.getToken());
        BigDecimal bill = sameToken.stream().map(Parcel::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PackageResponse(p.getId(), p.getToken(), p.getBinCode(), alloc.getPackageCount(), alloc.getCapacity(),
                newBin, p.getFloor(), p.getDescription(), p.getAmount(), sameToken.size(), bill, p.getCreatedAt());
    }

    private String newToken() {
        String token;
        do {
            StringBuilder sb = new StringBuilder("PP-");
            for (int i = 0; i < 6; i++) sb.append(TOKEN_CHARS.charAt(RANDOM.nextInt(TOKEN_CHARS.length())));
            token = sb.toString();
        } while (parcels.existsByTokenAndStatus(token, Parcel.Status.ALLOCATED));
        return token;
    }
}
