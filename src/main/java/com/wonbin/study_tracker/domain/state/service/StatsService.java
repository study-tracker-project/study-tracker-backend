package com.wonbin.study_tracker.domain.state.service;

import com.wonbin.study_tracker.domain.classification.service.AppDisplayNameService;
import com.wonbin.study_tracker.domain.log.repository.ActivityLogRepository;
import com.wonbin.study_tracker.domain.log.repository.BrowserLogRepository;
import com.wonbin.study_tracker.domain.session.dto.SessionResponse;
import com.wonbin.study_tracker.domain.session.entity.StudySession;
import com.wonbin.study_tracker.domain.session.repository.SessionLogNoteRepository;
import com.wonbin.study_tracker.domain.session.repository.StudySessionRepository;
import com.wonbin.study_tracker.domain.state.dto.StatsResponse;
import com.wonbin.study_tracker.domain.user.entity.User;
import com.wonbin.study_tracker.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatsService {

    private final StudySessionRepository sessionRepository;
    private final ActivityLogRepository activityLogRepository;
    private final BrowserLogRepository browserLogRepository;
    private final SessionLogNoteRepository sessionLogNoteRepository;
    private final UserRepository userRepository;
    private final AppDisplayNameService appDisplayNameService;

    // 하루 기준 시작/종료 시각 계산(day_change_hour 적용)
    public LocalDateTime[] getDayRange(Long userId, LocalDate date) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        int changeHour = user.getDayChangeHour();
        LocalDateTime start = date.atTime(LocalTime.of(changeHour, 0));
        LocalDateTime end = date.plusDays(1).atTime(LocalTime.of(changeHour, 0));
        return new LocalDateTime[]{start, end};
    }

    @Transactional(readOnly = true)
    public StatsResponse.TodaySummary getTodaySummary(Long userId) {
        LocalDateTime[] range = getDayRange(userId, LocalDate.now());

        List<StudySession> sessions = sessionRepository.findByUserIdAndStartedAtBetweenOrderByStartedAtAsc(
                userId, range[0], range[1]);

        int totalStudySec = sessions.stream()
                .mapToInt(StudySession::getStudySec).sum();
        int totalDistractSec = sessions.stream()
                .mapToInt(StudySession::getDistractSec).sum();
        int totalNeutralSec = sessions.stream()
                .mapToInt(StudySession::getNeutralSec).sum();

        List<StatsResponse.DistractItem> studyDetails = collectDetailsByCategory(userId, range, "STUDY");
        List<StatsResponse.DistractItem> distractDetails = collectDetailsByCategory(userId, range, "DISTRACT");
        List<StatsResponse.DistractItem> neutralDetails = collectDetailsByCategory(userId, range, "NEUTRAL");

        return StatsResponse.TodaySummary.builder()
                .totalStudySec(totalStudySec)
                .totalDistractSec(totalDistractSec)
                .totalNeutralSec(totalNeutralSec)
                .sessionCount(sessions.size())
                .topDistracts(distractDetails.stream().limit(5).collect(Collectors.toList()))
                .recentNotes(getRecentNotes(userId))
                .studyDetails(studyDetails)
                .distractDetails(distractDetails)
                .neutralDetails(neutralDetails)
                .build();
    }

    private List<StatsResponse.DistractItem> collectDetailsByCategory(
            Long userId, LocalDateTime[] range, String category) {
        List<StatsResponse.DistractItem> details = new ArrayList<>();

        for (Object[] row : activityLogRepository.findTopAppsByCategory(userId, range[0], range[1], category)) {
            details.add(StatsResponse.DistractItem.builder()
                    .name((String) row[0])
                    .totalSec(((Number) row[1]).intValue())
                    .build());
        }

        for (Object[] row : browserLogRepository.findTopDomainsByCategory(userId, range[0], range[1], category)) {
            details.add(StatsResponse.DistractItem.builder()
                    .name((String) row[0])
                    .totalSec(((Number) row[1]).intValue())
                    .build());
        }

        Map<String, String> names = appDisplayNameService.resolve(
                details.stream().map(StatsResponse.DistractItem::getName).toList());
        List<StatsResponse.DistractItem> named = details.stream()
                .map(d -> StatsResponse.DistractItem.builder()
                        .name(d.getName())
                        .displayName(names.get(d.getName()))
                        .totalSec(d.getTotalSec())
                        .build())
                .sorted((a, b) -> b.getTotalSec() - a.getTotalSec())
                .collect(Collectors.toList());
        return named;
    }

    private List<SessionResponse.LogNote> getRecentNotes(Long userId) {
        List<SessionResponse.LogNote> notes = sessionRepository.findFirstByUserIdAndEndedAtIsNotNullOrderByEndedAtDesc(userId)
                .map(session -> sessionLogNoteRepository.findBySessionId(session.getId()).stream()
                        .map(SessionResponse.LogNote::from)
                        .collect(Collectors.toList()))
                .orElse(List.of());
        Map<String, String> names = appDisplayNameService.resolve(
                notes.stream().map(SessionResponse.LogNote::getLogValue).toList());
        notes.forEach(n -> n.withDisplayName(names.get(n.getLogValue())));
        return notes;
    }

    @Transactional(readOnly = true)
    public List<StatsResponse.SessionSummary> getSessions(Long userId, LocalDate date) {
        LocalDateTime[] range = getDayRange(userId, date);

        return sessionRepository
                .findByUserIdAndStartedAtBetweenOrderByStartedAtAsc(
                        userId, range[0], range[1])
                .stream()
                .map(s -> StatsResponse.SessionSummary.builder()
                        .sessionId(s.getId())
                        .studyType(s.getStudyType())
                        .startedAt(s.getStartedAt())
                        .endedAt(s.getEndedAt())
                        .studySec(s.getStudySec())
                        .distractSec(s.getDistractSec())
                        .neutralSec(s.getNeutralSec())
                        .totalSec(s.getTotalSec())
                        .build())
                .collect(Collectors.toList());
    }

    public List<StatsResponse.DailyStat> getWeeklyStats(Long userId, LocalDate startDate) {
        List<StatsResponse.DailyStat> result = new ArrayList<>();

        for (int i = 0; i < 7; i++) {
            LocalDate date = startDate.plusDays(i);
            LocalDateTime[] range = getDayRange(userId, date);

            List<StudySession> sessions = sessionRepository
                    .findByUserIdAndStartedAtBetweenOrderByStartedAtAsc(
                            userId, range[0], range[1]);

            int studySec = sessions.stream()
                    .mapToInt(StudySession::getStudySec).sum();
            int distractSec = sessions.stream()
                    .mapToInt(StudySession::getDistractSec).sum();
            int neutralSec = sessions.stream()
                    .mapToInt(StudySession::getNeutralSec).sum();

            result.add(StatsResponse.DailyStat.builder()
                    .date(date)
                    .totalStudySec(studySec)
                    .totalDistractSec(distractSec)
                    .totalNeutralSec(neutralSec)
                    .sessionCount(sessions.size())
                    .build());
        }

        return result;
    }

    @Transactional(readOnly = true)
    public List<StatsResponse.NoteDailySummary> getWeeklyNotes(Long userId, LocalDate startDate) {
        List<StatsResponse.NoteDailySummary> result = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            result.add(buildNoteDailySummary(userId, startDate.plusDays(i)));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<StatsResponse.NoteDailySummary> getMonthlyNotes(Long userId, int year, int month) {
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());

        List<StatsResponse.NoteDailySummary> result = new ArrayList<>();
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            result.add(buildNoteDailySummary(userId, date));
        }
        return result;
    }

    private StatsResponse.NoteDailySummary buildNoteDailySummary(Long userId, LocalDate date) {
        LocalDateTime[] range = getDayRange(userId, date);

        List<StudySession> sessions = sessionRepository
                .findByUserIdAndStartedAtBetweenOrderByStartedAtAsc(userId, range[0], range[1]);

        int totalStudySec = sessions.stream().mapToInt(StudySession::getStudySec).sum();

        List<StatsResponse.SessionNoteGroup> sessionGroups = new ArrayList<>();
        for (StudySession session : sessions) {
            List<StatsResponse.StudyNoteItem> notes = sessionLogNoteRepository
                    .findBySessionId(session.getId()).stream()
                    .filter(n -> "STUDY".equals(n.getCategory()))
                    .map(n -> StatsResponse.StudyNoteItem.builder()
                            .logValue(n.getLogValue())
                            .category(n.getCategory())
                            .memo(n.getMemo())
                            .build())
                    .collect(Collectors.toList());

            if (notes.isEmpty()) continue;

            sessionGroups.add(StatsResponse.SessionNoteGroup.builder()
                    .sessionId(session.getId())
                    .studyType(session.getStudyType())
                    .notes(notes)
                    .build());
        }

        return StatsResponse.NoteDailySummary.builder()
                .date(date)
                .totalStudySec(totalStudySec)
                .sessions(sessionGroups)
                .build();
    }

    // 달력용 월별 순공 시간 (공부한 날만 포함)
    @Transactional(readOnly = true)
    public java.util.Map<String, Integer> getCalendar(Long userId, int year, int month) {
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());

        java.util.Map<String, Integer> result = new java.util.LinkedHashMap<>();

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            LocalDateTime[] range = getDayRange(userId, date);

            int studySec = sessionRepository
                    .findByUserIdAndStartedAtBetweenOrderByStartedAtAsc(userId, range[0], range[1])
                    .stream()
                    .mapToInt(StudySession::getStudySec).sum();

            if (studySec > 0) {
                result.put(date.toString(), studySec);
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<StatsResponse.DailyStat> getMonthlyStats(Long userId, int year, int month) {
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());

        List<StatsResponse.DailyStat> result = new ArrayList<>();

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            LocalDateTime[] range = getDayRange(userId, date);

            List<StudySession> sessions = sessionRepository
                    .findByUserIdAndStartedAtBetweenOrderByStartedAtAsc(
                            userId, range[0], range[1]);

            int studySec = sessions.stream()
                    .mapToInt(StudySession::getStudySec).sum();
            int distractSec = sessions.stream()
                    .mapToInt(StudySession::getDistractSec).sum();
            int neutralSec = sessions.stream()
                    .mapToInt(StudySession::getNeutralSec).sum();

            result.add(StatsResponse.DailyStat.builder()
                    .date(date)
                    .totalStudySec(studySec)
                    .totalDistractSec(distractSec)
                    .totalNeutralSec(neutralSec)
                    .sessionCount(sessions.size())
                    .build());
        }
        return result;
    }

}
