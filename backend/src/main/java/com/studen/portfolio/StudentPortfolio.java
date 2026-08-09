package com.studen.portfolio;

import com.studen.common.entity.BaseEntity;
import com.studen.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "student_portfolios")
public class StudentPortfolio extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false)
    private String headline;

    @Column(columnDefinition = "TEXT")
    private String bio;

    @Column(name = "experience_summary", columnDefinition = "TEXT")
    private String experienceSummary;

    @Column(name = "hourly_rate", precision = 10, scale = 2)
    private BigDecimal hourlyRate;

    @Column(name = "response_time")
    private String responseTime;

    private String location;

    @Column(name = "is_available", nullable = false)
    private boolean available = true;

    @Column(name = "public_slug", nullable = false, unique = true)
    private String publicSlug;

    @Column(name = "cover_image_url")
    private String coverImageUrl;

    public StudentPortfolio(User user, String headline, String publicSlug) {
        this.user = user;
        this.headline = headline;
        this.publicSlug = publicSlug;
    }
}
