package com.studen.resource;

// Deliberately simpler than PracticalAssessmentStatus (no REVIEW) — spec 7.7 doesn't call for a
// review workflow for resources, only publish/unpublish/archive. Unlike Question/
// PracticalAssessment, a Resource also stays editable after PUBLISHED (see AdminResourceService)
// since it isn't a scored or historical artifact whose fidelity needs freezing — only status
// transitions go through dedicated endpoints.
public enum ResourceStatus {
    DRAFT,
    PUBLISHED,
    ARCHIVED
}
