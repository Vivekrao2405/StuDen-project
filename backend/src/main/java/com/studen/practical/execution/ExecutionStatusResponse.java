package com.studen.practical.execution;

// The "internal execution-service health status the Run/Check API can use" -- lets the frontend
// show a proactive "execution is currently unavailable" banner before a student even clicks
// Run/Check, rather than only discovering it after a 503. Deliberately NOT wired into Spring Boot
// Actuator's aggregate /actuator/health: that drives Render's own container health checks, and one
// non-critical subsystem (code execution) being down must never make Render think the whole app
// is unhealthy and restart-loop a service where auth/portfolio/MCQ-assessments/etc all still work.
public record ExecutionStatusResponse(boolean codingAvailable, boolean sqlAvailable) {
}
