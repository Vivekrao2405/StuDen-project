package com.studen.integrity;

import com.studen.practical.PracticalAttempt;
import com.studen.practical.PracticalAttemptRepository;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deterministically recomputes one attempt's cached integrity summary from its complete,
 * ordered {@link AssessmentIntegrityEvent} log every time it's called -- never incrementally
 * mutated. This is what makes duplicate/out-of-order event delivery safe: replaying the same
 * events always produces the same result (idempotent), so
 * {@code IntegrityEventService#recordBatch}/{@code #heartbeat} can simply call this again after
 * every write with no special-casing.
 *
 * <p>Single ordered pass pairs each {@code TAB_HIDDEN}/{@code WINDOW_BLUR} with the next
 * {@code TAB_VISIBLE}/{@code WINDOW_FOCUS} (a simple open/close state machine, not a timestamp
 * window) -- this is also how correlated duplicate browser events (visibilitychange firing
 * alongside blur for the same physical tab switch) collapse into one logical away period: a
 * second open-side event arriving while one is already open is a no-op duplicate; a close-side
 * event arriving with nothing open is likewise a stray duplicate of an already-closed pairing.
 */
@Service
public class IntegrityScoringService {

    private final AssessmentIntegrityEventRepository eventRepository;
    private final PracticalAttemptRepository attemptRepository;
    private final IntegrityPolicyResolver policyResolver;
    private final IntegrityEventClassifier classifier;
    private final IntegrityScoringProperties properties;

    public IntegrityScoringService(AssessmentIntegrityEventRepository eventRepository,
            PracticalAttemptRepository attemptRepository, IntegrityPolicyResolver policyResolver,
            IntegrityEventClassifier classifier, IntegrityScoringProperties properties) {
        this.eventRepository = eventRepository;
        this.attemptRepository = attemptRepository;
        this.policyResolver = policyResolver;
        this.classifier = classifier;
        this.properties = properties;
    }

    @Transactional
    public void recompute(PracticalAttempt attempt) {
        IntegrityPolicy policy = policyResolver.resolve(attempt.getPracticalAssessment().getConfigurationJson());
        List<AssessmentIntegrityEvent> events = eventRepository.findAllByPracticalAttemptIdOrderByOccurredAtAsc(attempt.getId());

        AssessmentIntegrityEvent openAway = null;
        int awayOccurrence = 0;
        int copyDisallowedOccurrence = 0;
        int pasteDisallowedOccurrence = 0;
        int cutDisallowedOccurrence = 0;
        int fullscreenOccurrence = 0;

        int tabSwitchCount = 0;
        int copyCount = 0;
        int pasteCount = 0;
        int cutCount = 0;
        int fullscreenExitCount = 0;
        int multipleSessionCount = 0;

        int tabAwayDeduction = 0;
        int clipboardDeduction = 0;
        int fullscreenDeduction = 0;
        int multipleSessionDeduction = 0;
        int navigationDeduction = 0;

        int suspicious = 0;
        int critical = 0;

        for (AssessmentIntegrityEvent e : events) {
            IntegritySeverity severity;
            switch (e.getEventType()) {
                case TAB_HIDDEN, WINDOW_BLUR -> {
                    if (openAway == null) {
                        openAway = e;
                        severity = IntegritySeverity.INFO; // finalized once the pairing closes below
                    } else {
                        severity = classifier.classifyCorrelatedDuplicate();
                    }
                }
                case TAB_VISIBLE, WINDOW_FOCUS -> {
                    if (openAway != null) {
                        Duration duration = Duration.between(openAway.getOccurredAt(), e.getOccurredAt());
                        if (duration.isNegative()) {
                            duration = Duration.ZERO;
                        }
                        awayOccurrence++;
                        IntegritySeverity awaySeverity = classifier.classifyAway(duration, awayOccurrence);
                        openAway.setSeverity(awaySeverity);
                        openAway.setMetadata("{\"awayDurationMs\":" + duration.toMillis() + "}");
                        tabSwitchCount++;
                        tabAwayDeduction += properties.deductionFor(awaySeverity);
                        if (awaySeverity == IntegritySeverity.MEDIUM || awaySeverity == IntegritySeverity.HIGH) {
                            suspicious++;
                        } else if (awaySeverity == IntegritySeverity.CRITICAL) {
                            critical++;
                        }
                        openAway = null;
                        severity = IntegritySeverity.INFO; // the close-side marker itself
                    } else {
                        severity = classifier.classifyCorrelatedDuplicate();
                    }
                }
                case COPY_ATTEMPT -> {
                    if (!policy.allowCopy()) {
                        copyDisallowedOccurrence++;
                    }
                    severity = classifier.classifyClipboardAction(policy.allowCopy(), copyDisallowedOccurrence);
                    copyCount++;
                    clipboardDeduction += properties.deductionFor(severity);
                }
                case PASTE_ATTEMPT -> {
                    if (!policy.allowPaste()) {
                        pasteDisallowedOccurrence++;
                    }
                    severity = classifier.classifyClipboardAction(policy.allowPaste(), pasteDisallowedOccurrence);
                    pasteCount++;
                    clipboardDeduction += properties.deductionFor(severity);
                }
                case CUT_ATTEMPT -> {
                    if (!policy.allowCut()) {
                        cutDisallowedOccurrence++;
                    }
                    severity = classifier.classifyClipboardAction(policy.allowCut(), cutDisallowedOccurrence);
                    cutCount++;
                    clipboardDeduction += properties.deductionFor(severity);
                }
                case FULLSCREEN_EXITED -> {
                    if (policy.requireFullscreen()) {
                        fullscreenOccurrence++;
                    }
                    severity = classifier.classifyFullscreenExit(policy.requireFullscreen(), fullscreenOccurrence);
                    fullscreenExitCount++;
                    fullscreenDeduction += properties.deductionFor(severity);
                }
                case FULLSCREEN_ENTERED -> severity = IntegritySeverity.INFO;
                case NAVIGATION_VIOLATION -> {
                    severity = classifier.classifyNavigationViolation();
                    navigationDeduction += properties.deductionFor(severity);
                }
                case MULTIPLE_SESSION -> {
                    severity = classifier.classifyMultipleSession();
                    multipleSessionCount++;
                    multipleSessionDeduction += properties.deductionFor(severity);
                }
                default -> severity = IntegritySeverity.INFO;
            }
            e.setSeverity(severity);
            if (severity == IntegritySeverity.MEDIUM || severity == IntegritySeverity.HIGH) {
                suspicious++;
            } else if (severity == IntegritySeverity.CRITICAL) {
                critical++;
            }
        }

        // A hidden/blur with no matching return by the end of the log -- attempt expired, tab was
        // closed, or the return event simply hasn't arrived yet.
        if (openAway != null) {
            IntegritySeverity awaySeverity = classifier.classifyUnresolvedAway();
            openAway.setSeverity(awaySeverity);
            tabSwitchCount++;
            tabAwayDeduction += properties.deductionFor(awaySeverity);
            if (awaySeverity == IntegritySeverity.MEDIUM || awaySeverity == IntegritySeverity.HIGH) {
                suspicious++;
            }
        }

        tabAwayDeduction = Math.min(tabAwayDeduction, properties.getTabAwayCategoryCap());
        clipboardDeduction = Math.min(clipboardDeduction, properties.getClipboardCategoryCap());
        fullscreenDeduction = Math.min(fullscreenDeduction, properties.getFullscreenCategoryCap());
        multipleSessionDeduction = Math.min(multipleSessionDeduction, properties.getMultipleSessionCategoryCap());
        navigationDeduction = Math.min(navigationDeduction, properties.getNavigationCategoryCap());

        int totalDeduction = tabAwayDeduction + clipboardDeduction + fullscreenDeduction + multipleSessionDeduction
                + navigationDeduction;
        int score = Math.max(0, Math.min(properties.getBaseScore(), properties.getBaseScore() - totalDeduction));

        attempt.setIntegrityScore(score);
        attempt.setIntegrityStatus(properties.statusFor(score));
        attempt.setTabSwitchCount(tabSwitchCount);
        attempt.setCopyAttemptCount(copyCount);
        attempt.setPasteAttemptCount(pasteCount);
        attempt.setCutAttemptCount(cutCount);
        attempt.setFullscreenExitCount(fullscreenExitCount);
        attempt.setMultipleSessionCount(multipleSessionCount);
        attempt.setTotalIntegrityEventCount(events.size());
        attempt.setSuspiciousEventCount(suspicious);
        attempt.setCriticalEventCount(critical);

        eventRepository.saveAll(events);
        attemptRepository.save(attempt);
    }
}
