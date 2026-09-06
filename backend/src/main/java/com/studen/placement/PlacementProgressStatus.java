package com.studen.placement;

// Same three states as ResourceProgressStatus, kept as a separate enum so placement progress and
// learning-resource progress can evolve independently.
public enum PlacementProgressStatus {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETED
}
