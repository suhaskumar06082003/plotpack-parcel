package com.plotpack.parcel.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BinAllocationTest {
    private final Bin bin = new Bin(7L, "A-2-02", 2);

    @Test
    void newAllocationIsActiveWithSpace() {
        var a = new BinAllocation(bin, 42L);
        assertThat(a.isActive()).isTrue();
        assertThat(a.hasSpace()).isTrue();
    }

    @Test
    void cannotExceedCapacity() {
        var a = new BinAllocation(bin, 42L);
        a.addPackage();
        a.addPackage();
        assertThat(a.hasSpace()).isFalse();
        assertThatThrownBy(a::addPackage).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void lastPackageOutReleasesTheBin() {
        var a = new BinAllocation(bin, 42L);
        a.addPackage();
        a.addPackage();
        assertThat(a.removePackage()).isFalse();
        assertThat(a.removePackage()).isTrue();
        assertThat(a.getStatus()).isEqualTo(BinAllocation.Status.RELEASED);
        assertThat(a.hasSpace()).as("a released allocation is never reused").isFalse();
    }

    @Test
    void extraRemoveOnReleasedBinIsHarmless() {
        var a = new BinAllocation(bin, 42L);
        a.addPackage();
        a.removePackage();
        assertThat(a.removePackage()).isFalse();
        assertThat(a.getPackageCount()).isZero();
    }
}
