package com.studen.marketplace;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A student-supplied external link (GitHub, Live Demo, Behance, ...) for a service listing.
 * Mirrors {@code com.studen.showcase.ProjectLink} but kept as its own type — each feature owns
 * its own link value object in this codebase rather than sharing one across modules. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class ServiceLink {

    @Column(nullable = false)
    private String label;

    @Column(nullable = false)
    private String url;
}
