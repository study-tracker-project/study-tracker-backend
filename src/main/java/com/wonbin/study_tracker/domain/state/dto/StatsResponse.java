package com.wonbin.study_tracker.domain.state.dto;

import com.wonbin.study_tracker.domain.session.dto.SessionResponse;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class StatsResponse {

    @Getter
    @Builder
    public static class TodaySummary {
        private int totalStudySec;
        private int totalDistractSec;
        private int totalNeutralSec;
        private int sessionCount;
        private List<DistractItem> topDistracts;
        private List<SessionResponse.LogNote> recentNotes;   // 가장 최근 종료 세션의 노트 목록
        private List<DistractItem> studyDetails;
        private List<DistractItem> distractDetails;
        private List<DistractItem> neutralDetails;
    }

    @Getter
    @Builder
    public static class DistractItem {
        private String name;
        private String displayName;
        private int totalSec;
    }

    @Getter
    @Builder
    public static class TimelineBlock {
        private LocalDateTime blockStart;
        private String category;
        private int studySec;
        private int distractSec;
        private int idleSec;
        private boolean isManualEdited;
    }

    @Getter
    @Builder
    public static class SessionSummary {
        private Long sessionId;
        private String studyType;
        private LocalDateTime startedAt;
        private LocalDateTime endedAt;
        private int studySec;
        private int distractSec;
        private int neutralSec;
        private int totalSec;
    }

    @Getter
    @Builder
    public static class DailyStat {
        private LocalDate date;
        private int totalStudySec;
        private int totalDistractSec;
        private int totalNeutralSec;
        private int sessionCount;
    }

    @Getter
    @Builder
    public static class StudyNoteItem {
        private String logValue;
        private String category;
        private String memo;
    }

    @Getter
    @Builder
    public static class SessionNoteGroup {
        private Long sessionId;
        private String studyType;
        private List<StudyNoteItem> notes;
    }

    @Getter
    @Builder
    public static class NoteDailySummary {
        private LocalDate date;
        private int totalStudySec;
        private List<SessionNoteGroup> sessions;
    }
}
