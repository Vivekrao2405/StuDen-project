package com.studen.questionbank;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Intentionally minimal (create + list only, no edit/delete in Phase 7.1) — topics are created
// inline while authoring a question, the same UX pattern SkillController uses for custom skills.
@RestController
@RequestMapping("/api/v1/admin/topics")
@PreAuthorize("hasRole('ADMIN')")
public class AdminTopicController {

    private final QuestionBankService questionBankService;

    public AdminTopicController(QuestionBankService questionBankService) {
        this.questionBankService = questionBankService;
    }

    @GetMapping
    public List<TopicResponse> list(@RequestParam UUID skillId) {
        return questionBankService.listTopics(skillId);
    }

    @PostMapping
    public ResponseEntity<TopicResponse> create(@Valid @RequestBody TopicRequest request) {
        TopicResponse response = questionBankService.createTopic(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
