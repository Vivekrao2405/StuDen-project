package com.studen.practical;

import com.studen.security.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/my-practical-assessments")
public class MyPracticalAttemptsController {

    private final PracticalAttemptService attemptService;

    public MyPracticalAttemptsController(PracticalAttemptService attemptService) {
        this.attemptService = attemptService;
    }

    @GetMapping
    public PracticalPageResponse<MyPracticalAttemptSummaryResponse> myAttempts(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return attemptService.myAttempts(principal.getId(), page, size);
    }
}
