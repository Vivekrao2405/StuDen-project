package com.studen.education;

import com.studen.common.entity.BaseEntity;
import com.studen.portfolio.StudentPortfolio;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "education")
public class Education extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private StudentPortfolio portfolio;

    @Column(nullable = false)
    private String degree;

    @Column(name = "field_of_study")
    private String fieldOfStudy;

    @Column(nullable = false)
    private String institution;

    @Column(name = "start_year", nullable = false)
    private Integer startYear;

    @Column(name = "end_year")
    private Integer endYear;

    @Column(name = "is_current", nullable = false)
    private boolean current = false;

    public Education(StudentPortfolio portfolio, String degree, String institution, Integer startYear) {
        this.portfolio = portfolio;
        this.degree = degree;
        this.institution = institution;
        this.startYear = startYear;
    }
}
