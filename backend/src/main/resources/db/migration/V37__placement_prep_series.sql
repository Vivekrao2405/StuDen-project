-- Phase 5 (Placement Prep): the only genuinely missing piece of the data model — everything else
-- (placement_series, placement_modules, placement_module_items, student_series_progress,
-- student_module_item_progress) already exists from V31. Additive only.

-- Student-facing "Preparation Type" selector (spec §2/§3) has no home in the existing model.
-- Nullable: a series that is not narrowed to one preparation type simply leaves this unset.
ALTER TABLE placement_series ADD COLUMN preparation_type VARCHAR(30);

CREATE INDEX idx_placement_series_preparation_type ON placement_series (preparation_type);
