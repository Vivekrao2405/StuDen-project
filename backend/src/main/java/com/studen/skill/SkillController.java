package com.studen.skill;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/skills")
public class SkillController {

    private final SkillSearchService skillSearchService;

    public SkillController(SkillSearchService skillSearchService) {
        this.skillSearchService = skillSearchService;
    }

    @GetMapping("/search")
    public List<SkillResponse> search(@RequestParam(name = "q", required = false, defaultValue = "") String q) {
        return skillSearchService.search(q);
    }
}
