package com.studen.share;

import com.studen.common.entity.BaseEntity;
import com.studen.portfolio.StudentPortfolio;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "profile_shares")
public class ProfileShare extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false, unique = true)
    private StudentPortfolio portfolio;

    @Column(name = "share_title")
    private String shareTitle;

    @Column(name = "meta_description")
    private String metaDescription;

    @Column(name = "show_contact", nullable = false)
    private boolean showContact = true;

    @Column(name = "show_email", nullable = false)
    private boolean showEmail = true;

    public ProfileShare(StudentPortfolio portfolio) {
        this.portfolio = portfolio;
    }
}
