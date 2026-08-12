package com.studen.skill;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/skills")
public class SkillController {

    private final SkillSearchService skillSearchService;
    private final SkillService skillService;

    public SkillController(SkillSearchService skillSearchService, SkillService skillService) {
        this.skillSearchService = skillSearchService;
        this.skillService = skillService;
    }

    @GetMapping("/search")
    public List<SkillResponse> search(@RequestParam(name = "q", required = false, defaultValue = "") String q) {
        return skillSearchService.search(q);
    }

    @GetMapping("/categories")
    public List<String> getCategories() {
        return skillService.listCategories();
    }

    @PostMapping
    public ResponseEntity<SkillResponse> createCustomSkill(@Valid @RequestBody CreateSkillRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(skillService.createOrGetCustomSkill(request));
    }
}
