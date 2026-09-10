package com.wonbin.study_tracker.domain.classification.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "app_display_names")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppDisplayName {

    @Id
    @Column(name = "raw_value", length = 255)
    private String rawValue;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public AppDisplayName(String rawValue, String displayName) {
        this.rawValue = rawValue;
        this.displayName = displayName;
        this.createdAt = LocalDateTime.now();
    }
}
