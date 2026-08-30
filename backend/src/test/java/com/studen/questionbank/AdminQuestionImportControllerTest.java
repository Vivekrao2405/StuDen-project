package com.studen.questionbank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.studen.auth.AuthResponse;
import com.studen.auth.RegisterRequest;
import com.studen.skill.CreateSkillRequest;
import com.studen.skill.SkillResponse;
import com.studen.user.UserRepository;
import com.studen.user.UserRole;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "app.security.auth-rate-limit.max-requests=100000")
class AdminQuestionImportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    private static final String TWO_VALID_QUESTIONS = """
            ## Q1

            ### Question
            What is the output of the following code?

            ```python
            a = {1, 2, 3}
            b = {2, 3, 4}
            print(a & b)
            ```

            ### Options
            A. {1}
            B. {2, 3}
            C. {1, 2, 3, 4}
            D. {4}

            ### Answer
            B

            ### Explanation
            The & operator returns the intersection of two sets.

            ### Difficulty
            Easy

            ### Tag
            python-sets-operators

            ## Q2

            ### Question
            Booleans in Python are a subtype of int.

            ### Options
            A. True
            B. False

            ### Answer
            A

            ### Explanation
            bool is a subclass of int in Python.

            ### Difficulty
            Medium

            ### Tag
            python-basics
            """;

    private String registerAndGetToken(String email) throws Exception {
        RegisterRequest request = new RegisterRequest("Test User", email, "SecurePassword123");
        String body = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, AuthResponse.class).accessToken();
    }

    private String registerAdminAndGetToken(String email) throws Exception {
        String token = registerAndGetToken(email);
        userRepository.findByEmail(email).ifPresent(user -> user.setRole(UserRole.ADMIN));
        return token;
    }

    private UUID createSkill(String token, String name) throws Exception {
        String body = mockMvc.perform(post("/api/v1/skills")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateSkillRequest(name, "Practical Skills"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, SkillResponse.class).id();
    }

    private MockMultipartFile mdFile(String content) {
        return new MockMultipartFile("file", "questions.md", "text/markdown", content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void parse_validMarkdown_detectsQuestionsInferSingleAndTrueFalseTypes() throws Exception {
        String adminToken = registerAdminAndGetToken("import-parse-admin@example.com");

        String body = mockMvc.perform(multipart("/api/v1/admin/questions/import/parse")
                        .file(mdFile(TWO_VALID_QUESTIONS))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        ImportParseResponse response = objectMapper.readValue(body, ImportParseResponse.class);

        assertThat(response.totalDetected()).isEqualTo(2);
        assertThat(response.validCount()).isEqualTo(2);
        assertThat(response.errorCount()).isEqualTo(0);

        ImportedQuestionDraft q1 = response.questions().get(0);
        assertThat(q1.tag()).isEqualTo("python-sets-operators");
        assertThat(q1.questionType()).isEqualTo(QuestionType.MCQ_SINGLE);
        assertThat(q1.difficulty()).isEqualTo(Difficulty.EASY);
        assertThat(q1.options()).hasSize(4);
        assertThat(q1.options().get(1).isCorrect()).isTrue(); // "B" -> second option
        assertThat(q1.questionText()).contains("```python");

        ImportedQuestionDraft q2 = response.questions().get(1);
        assertThat(q2.questionType()).isEqualTo(QuestionType.TRUE_FALSE);
        assertThat(q2.tag()).isEqualTo("python-basics");
    }

    @Test
    void parse_missingTagAndBadAnswerLetter_reportsErrorsWithoutThrowing() throws Exception {
        String adminToken = registerAdminAndGetToken("import-error-admin@example.com");
        String markdown = """
                ## Q1

                ### Question
                What does this print?

                ### Options
                A. 1
                B. 2

                ### Answer
                E

                ### Difficulty
                Easy
                """;

        String body = mockMvc.perform(multipart("/api/v1/admin/questions/import/parse")
                        .file(mdFile(markdown))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        ImportParseResponse response = objectMapper.readValue(body, ImportParseResponse.class);

        assertThat(response.totalDetected()).isEqualTo(1);
        assertThat(response.errorCount()).isEqualTo(1);
        ImportedQuestionDraft q1 = response.questions().get(0);
        assertThat(q1.valid()).isFalse();
        assertThat(q1.errors()).anyMatch(e -> e.contains("does not match any option"));
        assertThat(q1.errors()).anyMatch(e -> e.contains("Tag is required"));
    }

    @Test
    void confirmImport_allValid_createsEveryQuestionAndTheyAppearInQuestionBank() throws Exception {
        String adminToken = registerAdminAndGetToken("import-confirm-admin@example.com");
        UUID skillId = createSkill(adminToken, "Import Confirm Skill");

        String parseBody = mockMvc.perform(multipart("/api/v1/admin/questions/import/parse")
                        .file(mdFile(TWO_VALID_QUESTIONS))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        ImportParseResponse parsed = objectMapper.readValue(parseBody, ImportParseResponse.class);

        ImportConfirmRequest confirmRequest = new ImportConfirmRequest(skillId, null, parsed.questions());
        String confirmBody = mockMvc.perform(post("/api/v1/admin/questions/import/confirm")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        ImportConfirmResponse confirmed = objectMapper.readValue(confirmBody, ImportConfirmResponse.class);

        assertThat(confirmed.importedCount()).isEqualTo(2);
        assertThat(confirmed.questionIds()).hasSize(2);

        for (UUID id : confirmed.questionIds()) {
            mockMvc.perform(get("/api/v1/admin/questions/" + id).header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void confirmImport_withAnInvalidQuestion_rejectsAndCreatesNothing() throws Exception {
        String adminToken = registerAdminAndGetToken("import-reject-admin@example.com");
        UUID skillId = createSkill(adminToken, "Import Reject Skill");

        ImportedQuestionDraft invalidDraft = new ImportedQuestionDraft(1, null, "Bad question?", QuestionType.MCQ_SINGLE,
                Difficulty.EASY, "why", null, null, null, false,
                java.util.List.of(new ImportedOptionDraft("A", true), new ImportedOptionDraft("B", false)),
                java.util.List.of("Tag is required"));

        ImportConfirmRequest confirmRequest = new ImportConfirmRequest(skillId, null, java.util.List.of(invalidDraft));
        mockMvc.perform(post("/api/v1/admin/questions/import/confirm")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmRequest)))
                .andExpect(status().isBadRequest());

        String listBody = mockMvc.perform(get("/api/v1/admin/questions?skillId=" + skillId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(listBody).contains("\"totalElements\":0");
    }

    private String templateBQuestion(String id, String skill, String questionText) {
        return """
                ## QUESTION

                ### ID
                %s

                ### SKILL
                %s

                ### DIFFICULTY
                easy

                ### TAGS
                java-basics, java-syntax

                ### QUESTION_TEXT
                %s

                ### OPTIONS
                A. class
                B. struct
                C. define
                D. type

                ### ANSWER
                A

                ### EXPLANATION
                The `class` keyword declares a class.

                ### END
                """.formatted(id, skill, questionText);
    }

    private int totalElementsForSkill(String adminToken, UUID skillId) throws Exception {
        String listBody = mockMvc.perform(get("/api/v1/admin/questions?skillId=" + skillId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var page = objectMapper.readTree(listBody);
        return page.get("totalElements").asInt();
    }

    @Test
    void parse_templateB_resolvesSkillFromFileWithoutRequiringGlobalPicker() throws Exception {
        String adminToken = registerAdminAndGetToken("import-tb-skill-admin@example.com");
        // A distinctive skill name/question text pair, never used anywhere else (including manual
        // live-server QA against this same shared test database) — Skill.name is globally unique
        // and createSkill() reuses an existing row by name, so a generic name like "Java" risks
        // silently inheriting questions from unrelated prior activity in this container.
        UUID skillId = createSkill(adminToken, "TemplateB Skill Resolution Test Skill");

        String parseBody = mockMvc.perform(multipart("/api/v1/admin/questions/import/parse")
                        .file(mdFile(templateBQuestion("java-basics-001", "TemplateB Skill Resolution Test Skill",
                                "Which access modifier limits visibility to the declaring class only?")))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        ImportParseResponse parsed = objectMapper.readValue(parseBody, ImportParseResponse.class);
        ImportedQuestionDraft draft = parsed.questions().get(0);

        assertThat(draft.valid()).describedAs(String.join("; ", draft.errors())).isTrue();
        assertThat(draft.skillId()).isEqualTo(skillId);
        assertThat(draft.skillName()).isEqualTo("TemplateB Skill Resolution Test Skill");

        // No global skillId supplied at all — the question's own resolved skill is sufficient.
        ImportConfirmRequest confirmRequest = new ImportConfirmRequest(null, null, parsed.questions());
        String confirmBody = mockMvc.perform(post("/api/v1/admin/questions/import/confirm")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        ImportConfirmResponse confirmed = objectMapper.readValue(confirmBody, ImportConfirmResponse.class);
        assertThat(confirmed.importedCount()).isEqualTo(1);

        String questionBody = mockMvc.perform(get("/api/v1/admin/questions/" + confirmed.questionIds().get(0))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        QuestionResponse created = objectMapper.readValue(questionBody, QuestionResponse.class);
        assertThat(created.skillId()).isEqualTo(skillId);
        assertThat(created.tag()).isEqualTo("java-basics"); // first of "TAGS", single-tag rule preserved
    }

    @Test
    void parse_templateB_unknownSkill_leavesSkillUnresolvedWithoutBlockingError_andGlobalPickerRecovers() throws Exception {
        String adminToken = registerAdminAndGetToken("import-tb-unknownskill-admin@example.com");
        UUID fallbackSkillId = createSkill(adminToken, "Fallback Skill");

        String parseBody = mockMvc.perform(multipart("/api/v1/admin/questions/import/parse")
                        .file(mdFile(templateBQuestion("java-basics-001", "no-such-skill-xyz", "Which keyword is used to declare a class?")))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        ImportParseResponse parsed = objectMapper.readValue(parseBody, ImportParseResponse.class);
        ImportedQuestionDraft draft = parsed.questions().get(0);

        assertThat(draft.skillId()).isNull();
        assertThat(draft.skillName()).isEqualTo("no-such-skill-xyz");
        assertThat(draft.valid()).describedAs(String.join("; ", draft.errors())).isTrue(); // recoverable, not a hard error

        // No fallback skill supplied -> confirm must reject rather than guess.
        ImportConfirmRequest withoutFallback = new ImportConfirmRequest(null, null, parsed.questions());
        mockMvc.perform(post("/api/v1/admin/questions/import/confirm")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(withoutFallback)))
                .andExpect(status().isBadRequest());

        // The admin picks a skill in the Preview screen -> recovers via the fallback.
        ImportConfirmRequest withFallback = new ImportConfirmRequest(fallbackSkillId, null, parsed.questions());
        mockMvc.perform(post("/api/v1/admin/questions/import/confirm")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(withFallback)))
                .andExpect(status().isCreated());
    }

    @Test
    void parse_templateB_duplicateAgainstExistingQuestion_flagsAndBlocksImport() throws Exception {
        String adminToken = registerAdminAndGetToken("import-tb-dup-admin@example.com");
        UUID skillId = createSkill(adminToken, "Java Dup Skill");
        String questionText = "Which keyword is used to declare a class in Java?";

        mockMvc.perform(post("/api/v1/admin/questions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "skillId": "%s",
                                  "questionText": "%s",
                                  "questionType": "MCQ_SINGLE",
                                  "difficulty": "EASY",
                                  "options": [
                                    {"optionText": "class", "displayOrder": 0, "isCorrect": true},
                                    {"optionText": "struct", "displayOrder": 1, "isCorrect": false}
                                  ]
                                }
                                """.formatted(skillId, questionText)))
                .andExpect(status().isCreated());

        String parseBody = mockMvc.perform(multipart("/api/v1/admin/questions/import/parse")
                        .file(mdFile(templateBQuestion("java-basics-001", "Java Dup Skill", questionText)))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        ImportParseResponse parsed = objectMapper.readValue(parseBody, ImportParseResponse.class);
        ImportedQuestionDraft draft = parsed.questions().get(0);

        assertThat(draft.duplicate()).isTrue();
        assertThat(draft.valid()).isFalse();
        assertThat(draft.errors()).anyMatch(e -> e.startsWith("Duplicate:"));

        ImportConfirmRequest confirmRequest = new ImportConfirmRequest(null, null, parsed.questions());
        mockMvc.perform(post("/api/v1/admin/questions/import/confirm")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmRequest)))
                .andExpect(status().isBadRequest());

        assertThat(totalElementsForSkill(adminToken, skillId)).isEqualTo(1); // still just the original
    }

    // The exact scenario from spec: if question #N in a batch fails deep persistence-time
    // validation (something the lightweight draft-level `errors` check didn't catch — here, an
    // MCQ_SINGLE with zero correct options, which QuestionValidationService rejects but
    // ImportedQuestionDraft.valid() doesn't independently verify), the whole transaction must be
    // doomed to roll back — 0 questions imported, never "the ones before the failure".
    //
    // Asserted via TestTransaction.isFlaggedForRollback() rather than a second MockMvc read in
    // this same test method: this test class is itself @Transactional, so confirmImport's own
    // @Transactional joins (rather than commits/rolls back) that already-open transaction — a
    // later read on the SAME connection would still see questions 1–2's uncommitted INSERTs via
    // ordinary read-your-own-writes visibility, which would look like "it didn't roll back" even
    // though the transaction is correctly marked rollback-only and will never actually commit
    // (see feedback_testing_and_tooling memory entries 3–5 for the same class of test artifact).
    // Confirmed for real via a live server + separate HTTP requests, not just this assertion.
    @Test
    void confirmImport_middleQuestionFailsDeepValidation_marksTransactionRollbackOnly() throws Exception {
        String adminToken = registerAdminAndGetToken("import-rollback-admin@example.com");
        UUID skillId = createSkill(adminToken, "Rollback Skill");

        List<ImportedQuestionDraft> drafts = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            boolean poison = i == 3;
            List<ImportedOptionDraft> options = List.of(
                    new ImportedOptionDraft("Option A", !poison), // poison question: no correct option at all
                    new ImportedOptionDraft("Option B", false));
            drafts.add(new ImportedQuestionDraft(i, null, "Rollback question " + i + "?", QuestionType.MCQ_SINGLE,
                    Difficulty.EASY, "why", "java-rollback-test", null, null, false, options, List.of()));
        }

        ImportConfirmRequest confirmRequest = new ImportConfirmRequest(skillId, null, drafts);
        mockMvc.perform(post("/api/v1/admin/questions/import/confirm")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmRequest)))
                .andExpect(status().is4xxClientError());

        assertThat(org.springframework.test.context.transaction.TestTransaction.isFlaggedForRollback()).isTrue();
    }
}
