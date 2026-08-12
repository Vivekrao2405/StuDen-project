package com.studen.showcase;

public record ProjectLinkResponse(String label, String url) {

    public static ProjectLinkResponse from(ProjectLink link) {
        return new ProjectLinkResponse(link.getLabel(), link.getUrl());
    }
}
