package com.plotpack.common.events;

public final class EventTypes {
    /** A package was placed in a bin; payload carries the customer's e-token. */
    public static final String PACKAGE_ALLOCATED = "PACKAGE_ALLOCATED";
    /** Everything under an e-token was handed over and the bins were released. */
    public static final String ORDER_DELIVERED = "ORDER_DELIVERED";

    private EventTypes() {
    }
}
