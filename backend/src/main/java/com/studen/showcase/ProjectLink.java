package com.studen.showcase;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A student-supplied external link (GitHub, live demo, Behance, ...). A simple value with no
 * independent lifecycle of its own, so it's embedded directly in {@link Project} rather than
 * being its own entity — the same idea as {@code AvailabilityOption}'s element collection, just
 * with two fields instead of an enum. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class ProjectLink {

    @Column(nullable = false)
    private String label;

    @Column(nullable = false)
    private String url;
}
