package com.studen.marketplace;

public sealed interface MarketplaceResultResponse permits StudentResultResponse, ServiceResultResponse {
    String type();
}
