package com.studen.integrity;

// Always computed server-side by IntegrityEventClassifier -- never accepted from the client.
// INFO events are still persisted (evidence/counting) but contribute zero score deduction.
public enum IntegritySeverity {
    INFO,
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
