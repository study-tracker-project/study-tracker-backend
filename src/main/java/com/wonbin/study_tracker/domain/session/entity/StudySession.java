package com.wonbin.study_tracker.domain.session.entity;

import com.wonbin.study_tracker.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "study_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class StudySession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "study_type", nullable = false, length = 10)
    private String studyType;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "target_sec")
    private Integer targetSec;

    @Column(name = "is_auto_ended", nullable = false)
    private boolean isAutoEnded;

    @Column(name = "total_sec", nullable = false)
    private int totalSec;

    @Column(name = "study_sec", nullable = false)
    private int studySec;

    @Column(name = "distract_sec", nullable = false)
    private int distractSec;

    @Column(name = "neutral_sec", nullable = false)
    private int neutralSec;

    @Column(name = "pause_sec", nullable = false)
    private int pauseSec;

    public void end(boolean isAutoEnded) {
        this.endedAt = LocalDateTime.now();
        this.isAutoEnded = isAutoEnded;
        this.totalSec = (int) java.time.Duration.between(startedAt, endedAt).getSeconds() - pauseSec;
    }

    public void addPauseSec(int sec) {
        this.pauseSec += sec;
    }

    // 로그 도착 시 즉시 누적
    public void addStudySec(int sec) {
        this.studySec += sec;
    }

    public void addDistractSec(int sec) {
        this.distractSec += sec;
    }

    public void addNeutralSec(int sec) {
        this.neutralSec += sec;
    }

    public void updateStudySec(int studySec, int distractSec, int neutralSec) {
        this.studySec = studySec;
        this.distractSec = distractSec;
        this.neutralSec = neutralSec;
    }

    public void extendTarget(int additionalSec) {
        if(this.targetSec != null) {
            this.targetSec += additionalSec;
        }
    }
}
