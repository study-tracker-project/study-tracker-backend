package com.wonbin.study_tracker.domain.session.dto;

import com.wonbin.study_tracker.domain.session.entity.StudySession;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

public class SessionResponse {

    @Getter
    @Builder
    public static class Detail{
        private Long sessionId;
        private String studyType;
        private LocalDateTime startedAt;
        private LocalDateTime endedAt;
        private Integer targetSec;
        private int totalSec;
        private int studySec;
        private int distractSec;
        private int neutralSec;
        private int pauseSec;
        private boolean isEnded;

        public static Detail from(StudySession session) {
            return Detail.builder()
                    .sessionId(session.getId())
                    .studyType(session.getStudyType())
                    .startedAt(session.getStartedAt())
                    .endedAt(session.getEndedAt())
                    .targetSec(session.getTargetSec())
                    .totalSec(session.getTotalSec())
                    .studySec(session.getStudySec())
                    .distractSec(session.getDistractSec())
                    .neutralSec(session.getNeutralSec())
                    .pauseSec(session.getPauseSec())
                    .isEnded(session.getEndedAt() != null)
                    .build();
        }

    }

    @Getter
    @Builder
    public static class LogSummaryItem {
        private String logType;    // APP / DOMAIN
        private String logValue;   // idea64.exe / youtube.com
        private int totalSec;      // 총 사용 시간
        private String category;   // 자동 분류 기본값
    }

    // 세션 완료 후 메모 조회용
    @Getter
    @Builder
    public static class LogNote {
        private Long id;
        private String logType;
        private String logValue;
        private String category;
        private String memo;

        public static LogNote from(com.wonbin.study_tracker.domain.session.entity.SessionLogNote note) {
            return LogNote.builder()
                    .id(note.getId())
                    .logType(note.getLogType())
                    .logValue(note.getLogValue())
                    .category(note.getCategory())
                    .memo(note.getMemo())
                    .build();
        }
    }
}
