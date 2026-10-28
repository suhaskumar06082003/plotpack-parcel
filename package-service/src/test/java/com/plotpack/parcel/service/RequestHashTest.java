package com.plotpack.parcel.service;

import com.plotpack.parcel.api.Dtos.CreatePackageRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class RequestHashTest {

    @Test
    void sameLogicalRequestHashesEqually() {
        var a = new CreatePackageRequest(1L, 2, "Silk saree", new BigDecimal("1999.00"));
        var b = new CreatePackageRequest(1L, 2, "Silk saree", new BigDecimal("1999"));
        assertThat(PackageOrchestrator.requestHash(a)).isEqualTo(PackageOrchestrator.requestHash(b));
    }

    @Test
    void differentBodyHashesDifferently() {
        var a = new CreatePackageRequest(1L, 2, "Silk saree", new BigDecimal("1999.00"));
        var b = new CreatePackageRequest(1L, 3, "Silk saree", new BigDecimal("1999.00"));
        assertThat(PackageOrchestrator.requestHash(a)).isNotEqualTo(PackageOrchestrator.requestHash(b));
    }
}
