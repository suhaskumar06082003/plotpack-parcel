package com.plotpack.parcel.api;

import com.plotpack.common.web.BusinessException;
import com.plotpack.common.web.ErrorCode;
import com.plotpack.parcel.api.Dtos.*;
import com.plotpack.parcel.domain.*;
import com.plotpack.parcel.service.DeliveryService;
import com.plotpack.parcel.service.PackageOrchestrator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/packages")
@Tag(name = "Packages", description = "Bin allocation (floor executive) and token-based pickup (ground floor)")
public class PackageController {

    private final PackageOrchestrator orchestrator;
    private final DeliveryService delivery;
    private final ParcelRepository parcels;
    private final BinRepository bins;
    private final BinAllocationRepository allocations;

    public PackageController(PackageOrchestrator orchestrator, DeliveryService delivery, ParcelRepository parcels,
                             BinRepository bins, BinAllocationRepository allocations) {
        this.orchestrator = orchestrator;
        this.delivery = delivery;
        this.parcels = parcels;
        this.bins = bins;
        this.allocations = allocations;
    }

    @PostMapping
    @Operation(summary = "Floor executive: create a package -> bin allocated -> e-token issued",
            description = "201 new package. 200 + Idempotent-Replayed:true when the same Idempotency-Key is retried. "
                    + "503 NO_FREE_BIN when the grid is full.")
    public ResponseEntity<PackageResponse> create(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                                  @Valid @RequestBody CreatePackageRequest request) throws InterruptedException {
        if (idempotencyKey.isBlank() || idempotencyKey.length() > 64) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED, "Idempotency-Key must be 1-64 chars");
        }
        var result = orchestrator.create(idempotencyKey, request);
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .header("Idempotent-Replayed", String.valueOf(result.replayed()))
                .body(result.response());
    }

    @GetMapping("/tokens/{token}")
    @Operation(summary = "Ground floor: look up an e-token -> which bins to open")
    public TokenView token(@PathVariable String token) {
        List<Parcel> all = parcels.findByTokenOrderByBinCodeAscIdAsc(token.trim().toUpperCase());
        if (all.isEmpty()) {
            throw new BusinessException(ErrorCode.TOKEN_NOT_FOUND, "No packages for e-token " + token);
        }
        boolean waiting = all.stream().anyMatch(p -> p.getStatus() == Parcel.Status.ALLOCATED);
        Map<String, List<Item>> byBin = all.stream().collect(Collectors.groupingBy(Parcel::getBinCode, TreeMap::new,
                Collectors.mapping(p -> new Item(p.getId(), p.getFloor(), p.getDescription(), p.getAmount(),
                        p.getStatus().name()), Collectors.toList())));
        Parcel first = all.get(0);
        return new TokenView(first.getToken(), waiting ? "WAITING" : "DELIVERED", first.getCustomerId(),
                first.getCustomerName(), first.getCustomerPhone(), all.size(), sum(all),
                byBin.entrySet().stream().map(e -> new BinItems(e.getKey(), e.getValue())).toList());
    }

    @PostMapping("/tokens/{token}/deliver")
    @Operation(summary = "Ground floor: hand over everything for the e-token and release the bins")
    public DeliveryResult deliver(@PathVariable String token) {
        return delivery.deliver(token.trim().toUpperCase());
    }

    @GetMapping("/tokens")
    @Operation(summary = "E-tokens with packages still waiting at the ground floor")
    public List<TokenView> waitingTokens() {
        Map<String, List<Parcel>> byToken = parcels.findByStatusOrderByIdAsc(Parcel.Status.ALLOCATED).stream()
                .collect(Collectors.groupingBy(Parcel::getToken, LinkedHashMap::new, Collectors.toList()));
        return byToken.values().stream().map(list -> {
            Parcel p = list.get(0);
            List<BinItems> binItems = list.stream().map(Parcel::getBinCode).distinct().sorted()
                    .map(code -> new BinItems(code, List.of())).toList();
            return new TokenView(p.getToken(), "WAITING", p.getCustomerId(), p.getCustomerName(), p.getCustomerPhone(),
                    list.size(), sum(list), binItems);
        }).toList();
    }

    @GetMapping("/bins")
    @Operation(summary = "The ground-floor grid with live occupancy")
    public List<GridCell> grid() {
        Map<Long, BinAllocation> active = allocations.findByStatus(BinAllocation.Status.ACTIVE).stream()
                .collect(Collectors.toMap(BinAllocation::getBinId, Function.identity()));
        Map<Long, Parcel> occupant = new HashMap<>();
        for (Parcel p : parcels.findByStatusOrderByIdAsc(Parcel.Status.ALLOCATED)) occupant.putIfAbsent(p.getAllocationId(), p);
        return bins.findAllByOrderByDistanceRankAsc().stream().map(b -> {
            BinAllocation a = active.get(b.getId());
            Parcel p = a == null ? null : occupant.get(a.getId());
            return new GridCell(b.getId(), b.getCode(), b.getRack(), b.getRowNo(), b.getColNo(), b.getCapacity(),
                    a == null ? 0 : a.getPackageCount(), p == null ? null : p.getCustomerName(), p == null ? null : p.getToken());
        }).toList();
    }

    @GetMapping("/stats")
    @Operation(summary = "Headline numbers for the dashboard")
    public Map<String, Object> stats() {
        var startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("totalBins", bins.count());
        m.put("occupiedBins", allocations.findByStatus(BinAllocation.Status.ACTIVE).size());
        m.put("packagesWaiting", parcels.countByStatus(Parcel.Status.ALLOCATED));
        m.put("deliveredToday", parcels.countByStatusAndDeliveredAtAfter(Parcel.Status.DELIVERED, startOfDay));
        return m;
    }

    private static BigDecimal sum(List<Parcel> list) {
        return list.stream().map(Parcel::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
