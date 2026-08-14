package com.studen.questionbank;

import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/skills/{skillId}/topics")
public class LearnerTopicController {

    private final LearnerQuestionService learnerQuestionService;

    public LearnerTopicController(LearnerQuestionService learnerQuestionService) {
        this.learnerQuestionService = learnerQuestionService;
    }

    @GetMapping
    public List<TopicResponse> list(@PathVariable UUID skillId) {
        return learnerQuestionService.listTopics(skillId);
    }
}
