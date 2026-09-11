package com.wonbin.study_tracker.domain.session.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "session_log_notes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class SessionLogNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private StudySession session;

    @Column(name = "log_type", nullable = false, length = 10)
    private String logType; // APP / DOMAIN

    @Column(name = "log_value", nullable = false, length = 255)
    private String logValue; // idea64.exe / youtube.com

    @Column(name = "category", nullable = false, length = 10)
    private String category; // STUDY / DISTRACT / NEUTRAL

    @Column(name = "memo", length = 500)
    private String memo;
}
