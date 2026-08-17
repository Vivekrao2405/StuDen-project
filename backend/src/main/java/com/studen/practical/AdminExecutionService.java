package com.studen.practical;

import com.studen.common.exception.InvalidRequestException;
import com.studen.common.exception.ResourceNotFoundException;
import com.studen.practical.execution.ExecutionMessages;
import com.studen.practical.execution.ExecutionOrchestrator;
import com.studen.practical.execution.PracticalExecutionResult;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Backs the admin-only "Test Question" tool: runs every test case (public + hidden) for an
 * admin-supplied solution directly against a {@code PracticalAssessment}, no {@code
 * PracticalAttempt} involved, nothing persisted -- lets admins validate a question before
 * publishing it. Reuses {@link ExecutionOrchestrator} directly, same as the student run/submit
 * path, so there is exactly one place that talks to the judge layer.
 */
@Service
public class AdminExecutionService {

    private final PracticalAssessmentRepository assessmentRepository;
    private final PracticalTestCaseRepository testCaseRepository;
    private final ExecutionOrchestrator executionOrchestrator;
    private final ObjectMapper objectMapper;

    public AdminExecutionService(PracticalAssessmentRepository assessmentRepository,
            PracticalTestCaseRepository testCaseRepository, ExecutionOrchestrator executionOrchestrator,
            ObjectMapper objectMapper) {
        this.assessmentRepository = assessmentRepository;
        this.testCaseRepository = testCaseRepository;
        this.executionOrchestrator = executionOrchestrator;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public AdminTestRunResponse testRun(UUID assessmentId, AdminTestRunRequest request) {
        PracticalAssessment assessment = assessmentRepository.findById(assessmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Practical assessment not found"));
        if (assessment.getPracticalType() != PracticalType.CODING && assessment.getPracticalType() != PracticalType.SQL) {
            throw new InvalidRequestException("Test-run is only available for CODING and SQL assessments");
        }

        List<PracticalTestCase> testCases = testCaseRepository
                .findAllByPracticalAssessmentIdOrderByDisplayOrderAsc(assessmentId);
        if (testCases.isEmpty()) {
            throw new InvalidRequestException("This assessment has no test cases configured yet");
        }

        PracticalExecutionResult result;
        if (assessment.getPracticalType() == PracticalType.CODING) {
            if (request.language() == null) {
                throw new InvalidRequestException("A language is required");
            }
            result = executionOrchestrator.runCoding(request.language(), request.sourceCode(), testCases);
        } else {
            boolean ordered = isOrderedSqlComparison(assessment.getConfigurationJson());
            result = executionOrchestrator.runSql(request.sourceCode(), testCases, ordered);
        }

        return toResponse(result, testCases);
    }

    private boolean isOrderedSqlComparison(String configurationJson) {
        if (configurationJson == null || configurationJson.isBlank()) {
            return false;
        }
        try {
            JsonNode node = objectMapper.readTree(configurationJson);
            JsonNode ordered = node.get("sqlOrderedComparison");
            return ordered != null && ordered.asBoolean(false);
        } catch (Exception e) {
            return false;
        }
    }

    private AdminTestRunResponse toResponse(PracticalExecutionResult result, List<PracticalTestCase> testCases) {
        Map<UUID, PracticalTestCase> byId = testCases.stream()
                .collect(Collectors.toMap(PracticalTestCase::getId, Function.identity()));
        List<AdminExecutionTestResultResponse> testResults = result.testResults().stream()
                .map(outcome -> {
                    PracticalTestCase testCase = byId.get(outcome.testCaseId());
                    return new AdminExecutionTestResultResponse(outcome.testCaseId(), outcome.hidden(), outcome.passed(),
                            testCase != null ? testCase.getInput() : null, testCase != null ? testCase.getExpectedOutput() : null,
                            outcome.actualOutput(), outcome.executionTimeMs(), outcome.status());
                }).toList();

        return new AdminTestRunResponse(result.status(), ExecutionMessages.forResult(result), result.compileError(),
                result.testsTotal() == null ? null : result.testsPassed(), result.testsTotal(), result.durationMs(),
                testResults);
    }
}
