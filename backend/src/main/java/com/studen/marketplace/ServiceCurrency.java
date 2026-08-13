package com.studen.marketplace;

// A single value today (StuDen launches in India) — additive-only to extend later, same pattern
// as MarketplaceCategory: VARCHAR + EnumType.STRING with no DB CHECK constraint, so adding a
// currency later needs no migration.
public enum ServiceCurrency {
    INR
}
