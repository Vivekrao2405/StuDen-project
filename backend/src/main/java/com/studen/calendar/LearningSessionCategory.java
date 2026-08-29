package com.studen.calendar;

// Purely a display/organization label the student (or the study-plan generator) attaches when a
// session is scheduled -- never inferred from resource type. PRACTICE is the only category the
// backend ever assigns automatically (the study plan's resource-less "Practice / Revision" slot);
// every other slot defaults to LEARNING unless the student picks otherwise.
public enum LearningSessionCategory {
    LEARNING,
    PRACTICE,
    PROJECT,
    ASSESSMENT
}
