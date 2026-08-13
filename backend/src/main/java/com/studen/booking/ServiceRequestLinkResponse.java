package com.studen.booking;

public record ServiceRequestLinkResponse(String label, String url) {

    public static ServiceRequestLinkResponse from(ServiceRequestLink link) {
        return new ServiceRequestLinkResponse(link.getLabel(), link.getUrl());
    }
}
