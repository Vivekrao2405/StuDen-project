package com.studen.questionbank;

import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Foundation for Phase 7.2's Assessment Engine (spec §30) — requires only standard authentication
// (SecurityConfig's anyRequest().authenticated() already covers this), NOT the ADMIN role. Every
// response here is built from LearnerQuestionResponse/LearnerOptionResponse, which structurally
// cannot carry isCorrect/explanation/status. Only PUBLISHED questions are ever returned.
@RestController
@RequestMapping("/api/v1/skills/{skillId}/questions")
public class LearnerQuestionController {

    private final LearnerQuestionService learnerQuestionService;

    public LearnerQuestionController(LearnerQuestionService learnerQuestionService) {
        this.learnerQuestionService = learnerQuestionService;
    }

    @GetMapping
    public PageResponse<LearnerQuestionResponse> list(@PathVariable UUID skillId,
            @RequestParam(required = false) UUID topicId,
            @RequestParam(required = false) Difficulty difficulty,
            @RequestParam(required = false) QuestionType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return learnerQuestionService.listPublished(skillId, topicId, difficulty, type, page, size);
    }
}
