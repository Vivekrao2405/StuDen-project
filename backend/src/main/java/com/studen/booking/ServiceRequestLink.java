package com.studen.booking;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A requester-supplied reference link (GitHub, Figma, Drive, ...) attached to a service request.
 * Mirrors {@code com.studen.marketplace.ServiceLink} but kept as its own type — each feature owns
 * its own link value object in this codebase rather than sharing one across modules. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class ServiceRequestLink {

    @Column(nullable = false)
    private String label;

    @Column(nullable = false)
    private String url;
}
