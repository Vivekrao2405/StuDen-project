package com.studen.marketplace;

public record ServiceLinkResponse(String label, String url) {

    public static ServiceLinkResponse from(ServiceLink link) {
        return new ServiceLinkResponse(link.getLabel(), link.getUrl());
    }
}
