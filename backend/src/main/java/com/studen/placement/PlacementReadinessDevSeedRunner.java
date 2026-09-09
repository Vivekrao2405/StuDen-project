package com.studen.placement;

import com.studen.questionbank.Difficulty;
import com.studen.questionbank.QuestionBankService;
import com.studen.questionbank.QuestionOptionRequest;
import com.studen.questionbank.QuestionRequest;
import com.studen.questionbank.QuestionResponse;
import com.studen.questionbank.QuestionType;
import com.studen.skill.Skill;
import com.studen.skill.SkillRepository;
import com.studen.user.User;
import com.studen.user.UserRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Optional, opt-in development seed: a usable "Data Analyst Placement Readiness" assessment, so a
 * fresh dev/test environment can exercise the full Phase 3 flow (start -> answer -> submit ->
 * skill breakdown -> gaps) without an admin manually building one first first. Off by default —
 * gated by {@code app.placement.seed-dev-data} (default false) — this must never run
 * unintentionally in production; the real, permanent fix for "no assessment configured" is the
 * Admin Readiness Assessment UI, not this runner.
 *
 * <p>Mirrors {@link com.studen.user.AdminBootstrapRunner}'s idempotent, no-op-if-not-ready posture:
 * skips silently (with a log line) if no bootstrap admin is configured/registered yet, if the
 * "Data Analyst" role from the Phase 0 seed (V32) is missing, or if this exact assessment title
 * already exists (never runs twice). Reuses {@link QuestionBankService}/{@link
 * PlacementAssessmentService} end to end — the same validation/publish rules an admin using the UI
 * would go through, not a second content-creation path.
 *
 * <p>Only skills the Data Analyst RoleSkill mapping (V32) actually requires AND that already exist
 * in the shared skill catalog are seeded; "Data Interpretation" (named in the source spec but not
 * present as a catalog skill) is deliberately skipped rather than inventing one. Every question is
 * generic, factual, single-correct-answer MCQ content — no fabricated company-specific claims.
 */
@Component
@ConditionalOnProperty(name = "app.placement.seed-dev-data", havingValue = "true")
public class PlacementReadinessDevSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PlacementReadinessDevSeedRunner.class);
    private static final String ASSESSMENT_TITLE = "Data Analyst Placement Readiness (Dev Seed)";

    // skillNormalizedName -> ordered [questionText, correctOption, wrongOption, explanation] triples.
    private static final Map<String, List<String[]>> QUESTIONS_BY_SKILL = new LinkedHashMap<>();
    static {
        QUESTIONS_BY_SKILL.put("sql", List.of(
                new String[] {"Which SQL keyword removes duplicate rows from a result set?", "DISTINCT", "UNIQUE",
                        "DISTINCT filters duplicate rows from the query result."},
                new String[] {"Which SQL clause filters groups after a GROUP BY?", "HAVING", "WHERE",
                        "HAVING filters aggregated groups; WHERE filters rows before grouping."},
                new String[] {"Which SQL join returns all rows from the left table plus matched rows from the right?",
                        "LEFT JOIN", "INNER JOIN", "A LEFT JOIN keeps every left-table row even without a match."}));
        QUESTIONS_BY_SKILL.put("python", List.of(
                new String[] {"Which built-in Python function returns the number of items in a list?", "len()", "size()",
                        "len() returns the length of a sequence such as a list."},
                new String[] {"Which keyword defines a function in Python?", "def", "func",
                        "Functions are declared with the def keyword."},
                new String[] {"Which Python data type is an ordered, immutable sequence?", "tuple", "dict",
                        "A tuple is ordered and cannot be modified after creation."}));
        QUESTIONS_BY_SKILL.put("pandas", List.of(
                new String[] {"Which pandas DataFrame method removes rows containing missing values?", "dropna()",
                        "fillna()", "dropna() removes rows/columns with NaN values."},
                new String[] {"Which pandas object represents a single labeled column of data?", "Series", "Index",
                        "A pandas Series is a one-dimensional labeled array."},
                new String[] {"Which pandas method returns summary statistics (count, mean, std) for numeric columns?",
                        "describe()", "info()", "describe() computes descriptive statistics."}));
        QUESTIONS_BY_SKILL.put("statistics", List.of(
                new String[] {"Which measure of central tendency is most affected by outliers?", "Mean", "Median",
                        "Extreme values pull the mean but not the median."},
                new String[] {"What is the middle value of a sorted dataset called?", "Median", "Mode",
                        "The median is the middle value when data is sorted."},
                new String[] {"Which measure describes how spread out values are around the mean?", "Standard deviation",
                        "Median", "Standard deviation quantifies dispersion around the mean."}));
        QUESTIONS_BY_SKILL.put("excel", List.of(
                new String[] {"Which Excel function looks up a value in one column and returns a value from another column in the same row?",
                        "VLOOKUP", "SUMIF", "VLOOKUP searches a column and returns a corresponding value."},
                new String[] {"Which Excel feature summarizes and aggregates large datasets interactively?", "PivotTable",
                        "Conditional Formatting", "PivotTables let you group and aggregate data interactively."},
                new String[] {"Which Excel function calculates the arithmetic mean of a range?", "AVERAGE", "MEDIAN",
                        "AVERAGE computes the arithmetic mean of the given values."}));
        QUESTIONS_BY_SKILL.put("power bi", List.of(
                new String[] {"What is the primary formula language used for calculations in Power BI?", "DAX", "SQL",
                        "DAX (Data Analysis Expressions) powers Power BI measures and calculated columns."},
                new String[] {"Which Power BI view is used to design the visual layout of a dashboard?", "Report view",
                        "Data view", "Report view is where visuals are arranged on a page."},
                new String[] {"Which Power BI component holds a custom calculation defined with DAX?", "Measure",
                        "Filter", "Measures are DAX calculations used in visuals."}));
        QUESTIONS_BY_SKILL.put("data visualization", List.of(
                new String[] {"Which chart type is best suited for showing a trend over time?", "Line chart", "Pie chart",
                        "Line charts plot values over a continuous axis such as time."},
                new String[] {"Which chart type best shows proportions of a whole?", "Pie chart", "Scatter plot",
                        "Pie charts divide a whole into proportional slices."},
                new String[] {"Which chart type best shows the relationship between two numeric variables?", "Scatter plot",
                        "Bar chart", "Scatter plots reveal correlation between two numeric variables."}));
        QUESTIONS_BY_SKILL.put("numpy", List.of(
                new String[] {"Which NumPy object is the core n-dimensional array type?", "ndarray", "Series",
                        "ndarray is NumPy's fundamental n-dimensional array."},
                new String[] {"Which NumPy function returns the mean of array elements?", "numpy.mean()", "numpy.sum()",
                        "numpy.mean() computes the arithmetic mean of array elements."},
                new String[] {"Which NumPy method changes an array's shape without changing its data?", "reshape()",
                        "transpose()", "reshape() returns the same data laid out in a new shape."}));
        QUESTIONS_BY_SKILL.put("business analysis", List.of(
                new String[] {"Which technique evaluates Strengths, Weaknesses, Opportunities and Threats?", "SWOT analysis",
                        "Root cause analysis", "SWOT analysis structures internal/external factors into those four areas."},
                new String[] {"What document captures what a business needs from a project?", "Business Requirements Document",
                        "Test Plan", "A BRD records the business's needs and objectives for a project."},
                new String[] {"Which technique visualizes a business process as a sequence of steps?", "Process flow diagram",
                        "Gantt chart", "A process flow diagram maps steps and decision points in a process."}));
    }

    private final UserRepository userRepository;
    private final PlacementRoleRepository roleRepository;
    private final SkillRepository skillRepository;
    private final PlacementAssessmentRepository assessmentRepository;
    private final QuestionBankService questionBankService;
    private final PlacementAssessmentService placementAssessmentService;
    private final String bootstrapEmail;

    public PlacementReadinessDevSeedRunner(UserRepository userRepository, PlacementRoleRepository roleRepository,
            SkillRepository skillRepository, PlacementAssessmentRepository assessmentRepository,
            QuestionBankService questionBankService, PlacementAssessmentService placementAssessmentService,
            @Value("${app.admin.bootstrap-email:}") String bootstrapEmail) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.skillRepository = skillRepository;
        this.assessmentRepository = assessmentRepository;
        this.questionBankService = questionBankService;
        this.placementAssessmentService = placementAssessmentService;
        this.bootstrapEmail = bootstrapEmail;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (assessmentRepository.existsByTitle(ASSESSMENT_TITLE)) {
            return;
        }
        if (bootstrapEmail == null || bootstrapEmail.isBlank()) {
            log.info("Skipping placement readiness dev seed: no ADMIN_BOOTSTRAP_EMAIL configured.");
            return;
        }
        User admin = userRepository.findByEmail(bootstrapEmail.trim()).orElse(null);
        if (admin == null) {
            log.info("Skipping placement readiness dev seed: bootstrap admin account has not registered yet.");
            return;
        }
        PlacementRole role = roleRepository.findByNormalizedName("data analyst").orElse(null);
        if (role == null) {
            log.warn("Skipping placement readiness dev seed: 'Data Analyst' placement role not found.");
            return;
        }

        List<PlacementAssessmentQuestionRequest> links = new java.util.ArrayList<>();
        int order = 0;
        for (Map.Entry<String, List<String[]>> entry : QUESTIONS_BY_SKILL.entrySet()) {
            Skill skill = skillRepository.findByNormalizedName(entry.getKey()).orElse(null);
            if (skill == null) {
                log.warn("Skipping dev-seed questions for skill '{}': not found in the skill catalog.", entry.getKey());
                continue;
            }
            for (String[] q : entry.getValue()) {
                QuestionRequest request = new QuestionRequest(skill.getId(), null, q[0], QuestionType.MCQ_SINGLE,
                        Difficulty.MEDIUM, q[3], null, "placement-dev-seed",
                        List.of(new QuestionOptionRequest(q[1], 0, true), new QuestionOptionRequest(q[2], 1, false)));
                QuestionResponse question = questionBankService.create(admin.getId(), request);
                questionBankService.submitForReview(question.id());
                QuestionResponse published = questionBankService.publish(question.id(), admin.getId());
                links.add(new PlacementAssessmentQuestionRequest(published.id(), null, order++, 1));
            }
        }
        if (links.isEmpty()) {
            log.warn("Skipping placement readiness dev seed: none of the expected Data Analyst skills were found.");
            return;
        }

        PlacementAssessmentRequest assessmentRequest = new PlacementAssessmentRequest(ASSESSMENT_TITLE,
                "Development seed assessment covering the skills the Data Analyst role requires — "
                        + "generic knowledge questions, not company-specific content.",
                role.getId(), Difficulty.MEDIUM, 45, 60);
        PlacementAssessmentDetailResponse assessment = placementAssessmentService.create(admin.getId(), assessmentRequest);
        for (PlacementAssessmentQuestionRequest link : links) {
            placementAssessmentService.addQuestion(assessment.id(), link);
        }
        placementAssessmentService.setStatus(assessment.id(), PlacementContentStatus.PUBLISHED);
        log.info("Seeded and published '{}' with {} questions across {} skills.", ASSESSMENT_TITLE, links.size(),
                QUESTIONS_BY_SKILL.size());
    }
}
