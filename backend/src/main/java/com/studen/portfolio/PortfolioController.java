package com.studen.portfolio;

import com.studen.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/portfolio")
public class PortfolioController {

    private final PortfolioService portfolioService;

    public PortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @PostMapping
    public ResponseEntity<PortfolioResponse> createPortfolio(@AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody PortfolioRequest request) {
        PortfolioResponse response = portfolioService.createPortfolio(principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/me")
    public PortfolioResponse getMyPortfolio(@AuthenticationPrincipal UserPrincipal principal) {
        return portfolioService.getMyPortfolio(principal.getId());
    }

    @PutMapping("/me")
    public PortfolioResponse updateMyPortfolio(@AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody PortfolioRequest request) {
        return portfolioService.updateMyPortfolio(principal.getId(), request);
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteMyPortfolio(@AuthenticationPrincipal UserPrincipal principal) {
        portfolioService.deleteMyPortfolio(principal.getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/me/cover-image")
    public PortfolioResponse uploadCoverImage(@AuthenticationPrincipal UserPrincipal principal,
            @RequestParam("file") MultipartFile file) {
        return portfolioService.uploadCoverImage(principal.getId(), file);
    }

    @DeleteMapping("/me/cover-image")
    public PortfolioResponse removeCoverImage(@AuthenticationPrincipal UserPrincipal principal) {
        return portfolioService.removeCoverImage(principal.getId());
    }
}
