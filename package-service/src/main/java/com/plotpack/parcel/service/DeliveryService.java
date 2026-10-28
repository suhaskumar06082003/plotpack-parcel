package com.plotpack.parcel.service;

import com.plotpack.common.events.EventTypes;
import com.plotpack.common.events.PlotPackEvent;
import com.plotpack.common.events.Topics;
import com.plotpack.common.outbox.OutboxWriter;
import com.plotpack.common.web.BusinessException;
import com.plotpack.common.web.ErrorCode;
import com.plotpack.parcel.api.Dtos.DeliveryResult;
import com.plotpack.parcel.domain.BinAllocation;
import com.plotpack.parcel.domain.BinAllocationRepository;
import com.plotpack.parcel.domain.Parcel;
import com.plotpack.parcel.domain.ParcelRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * Ground floor: customer tells the e-token -> hand over every waiting package -> free the bins.
 * Runs in one transaction with row locks, so a double click (or two counter staff) can't deliver twice.
 */
@Service
public class DeliveryService {
    private final ParcelRepository parcels;
    private final BinAllocationRepository allocations;
    private final OutboxWriter outbox;

    public DeliveryService(ParcelRepository parcels, BinAllocationRepository allocations, OutboxWriter outbox) {
        this.parcels = parcels;
        this.allocations = allocations;
        this.outbox = outbox;
    }

    @Transactional
    public DeliveryResult deliver(String token) {
        List<Parcel> waiting = parcels.lockWaitingByToken(token);
        if (waiting.isEmpty()) {
            if (parcels.findByTokenOrderByBinCodeAscIdAsc(token).isEmpty()) {
                throw new BusinessException(ErrorCode.TOKEN_NOT_FOUND, "No packages for e-token " + token);
            }
            throw new BusinessException(ErrorCode.ALREADY_DELIVERED, "E-token " + token + " was already delivered");
        }

        Set<String> visited = new TreeSet<>();
        List<String> released = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Parcel p : waiting) {
            p.markDelivered();
            visited.add(p.getBinCode());
            total = total.add(p.getAmount());
            BinAllocation alloc = allocations.findByIdForUpdate(p.getAllocationId()).orElseThrow();
            if (alloc.removePackage()) {
                released.add(alloc.getBinCode());
            }
        }

        Parcel first = waiting.get(0);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("token", token);
        payload.put("customerName", first.getCustomerName());
        payload.put("customerPhone", first.getCustomerPhone());
        payload.put("packageCount", waiting.size());
        payload.put("billTotal", total);
        payload.put("binsReleased", released);
        outbox.append(Topics.PACKAGE_EVENTS, token, PlotPackEvent.of(EventTypes.ORDER_DELIVERED, token, payload));

        return new DeliveryResult(token, first.getCustomerName(), waiting.size(), total, List.copyOf(visited), released);
    }
}
