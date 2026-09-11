package com.wonbin.study_tracker.domain.session.service;

import com.wonbin.study_tracker.domain.classification.service.AppDisplayNameService;
import com.wonbin.study_tracker.domain.classification.service.ClassificationService;
import com.wonbin.study_tracker.domain.log.entity.ActivityLog;
import com.wonbin.study_tracker.domain.log.entity.BrowserLog;
import com.wonbin.study_tracker.domain.log.repository.ActivityLogRepository;
import com.wonbin.study_tracker.domain.log.repository.BrowserLogRepository;
import com.wonbin.study_tracker.domain.session.dto.SessionRequest;
import com.wonbin.study_tracker.domain.session.dto.SessionResponse;
import com.wonbin.study_tracker.domain.session.entity.SessionLogNote;
import com.wonbin.study_tracker.domain.session.entity.StudySession;
import com.wonbin.study_tracker.domain.session.repository.SessionLogNoteRepository;
import com.wonbin.study_tracker.domain.session.repository.StudySessionRepository;
import com.wonbin.study_tracker.domain.user.entity.User;
import com.wonbin.study_tracker.domain.user.repository.UserRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SessionService {

    private final StudySessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final ActivityLogRepository activityLogRepository;
    private final BrowserLogRepository browserLogRepository;
    private final SessionLogNoteRepository sessionLogNoteRepository;
    private final ClassificationService classificationService;
    private final AppDisplayNameService appDisplayNameService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public SessionResponse.Detail start(Long userId, SessionRequest.Start request) {
        sessionRepository.findByUserIdAndEndedAtIsNull(userId)
                .ifPresent(s -> {
                    throw new IllegalArgumentException("이미 진행중인 세션이 있습니다.");
                });

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        StudySession session = StudySession.builder()
                .user(user)
                .studyType(request.getStudyType())
                .startedAt(LocalDateTime.now())
                .targetSec((request.getTargetSec()))
                .isAutoEnded(false)
                .totalSec(0)
                .studySec(0)
                .distractSec(0)
                .neutralSec(0)
                .pauseSec(0)
                .build();

        sessionRepository.save(session);
        eventPublisher.publishEvent(new SessionEventBroadcastRequested(userId, SessionEventType.STARTED, session.getId()));
        return SessionResponse.Detail.from(session);
    }

    @Transactional
    public SessionResponse.Detail end(Long userId, Long sessionId, boolean isAutoEnded) {
        StudySession session = getSessionByUser(userId, sessionId);

        validateSessionNotEnded(session);

        session.end(isAutoEnded);  // studySec/distractSec은 이미 실시간으로 누적되어 있음
        eventPublisher.publishEvent(new SessionEventBroadcastRequested(userId, SessionEventType.ENDED, sessionId));

        return SessionResponse.Detail.from(session);
    }

    @Transactional
    public SessionResponse.Detail pause(Long userId, Long sessionId) {
        StudySession session = getSessionByUser(userId, sessionId);

        validateSessionNotEnded(session);

        // pause 시각을 기록하기 위해 pausedAt을 세션에 저장할 수도 있지만
        // MVP에서는 클라이언트가 resume 시 경과 시간을 보내는 방식으로 처리
        eventPublisher.publishEvent(new SessionEventBroadcastRequested(userId, SessionEventType.PAUSED, sessionId));

        return SessionResponse.Detail.from(session);
    }

    @Transactional
    public SessionResponse.Detail resume(Long userId, Long sessionId, int pauseSec) {
        StudySession session = getSessionByUser(userId, sessionId);

        validateSessionNotEnded(session);

        session.addPauseSec(pauseSec);
        eventPublisher.publishEvent(new SessionEventBroadcastRequested(userId, SessionEventType.RESUMED, sessionId));
        return SessionResponse.Detail.from(session);
    }

    @Transactional
    public SessionResponse.Detail extend(Long userId, Long sessionId, SessionRequest.Extend request) {
        StudySession session = getSessionByUser(userId, sessionId);
        validateSessionNotEnded(session);

        session.extendTarget(request.getAdditionalSec());
        return SessionResponse.Detail.from(session);

    }

    @Transactional(readOnly = true)
    public SessionResponse.Detail getSession(Long userId, Long sessionId) {
        return SessionResponse.Detail.from(getSessionByUser(userId, sessionId));
    }

    @Transactional(readOnly = true)
    public List<SessionResponse.LogNote> getNotes(Long userId, Long sessionId) {
        getSessionByUser(userId, sessionId); // 접근 권한 검증

        Map<String, Integer> durations = buildDurationMap(sessionId);
        List<SessionResponse.LogNote> notes = sessionLogNoteRepository.findBySessionId(sessionId).stream()
                .map(SessionResponse.LogNote::from)
                .toList();
        Map<String, String> names = appDisplayNameService.resolve(
                notes.stream().map(SessionResponse.LogNote::getLogValue).toList());
        notes.forEach(n -> {
            n.withDisplayName(names.get(n.getLogValue()));
            n.withTotalSec(durations.getOrDefault(n.getLogType() + "::" + n.getLogValue(), 0));
        });
        return notes;
    }

    // 세션의 ActivityLog/BrowserLog를 앱/도메인별로 합산한다("APP::idea64.exe" -> 총 초).
    // 완료 팝업(log-summary), 노트 조회, 완료 저장(finalize)이 전부 같은 원본 로그에서
    // 이 시간을 구하므로 한 곳에 모아 재사용한다.
    private Map<String, Integer> buildDurationMap(Long sessionId) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (ActivityLog log : activityLogRepository.findBySessionId(sessionId)) {
            map.merge("APP::" + log.getAppName(), log.getDurationSec(), Integer::sum);
        }
        for (BrowserLog log : browserLogRepository.findBySessionId(sessionId)) {
            map.merge("DOMAIN::" + log.getDomain(), log.getDurationSec(), Integer::sum);
        }
        return map;
    }

    @Transactional(readOnly = true)
    public Optional<SessionResponse.Detail> getActiveSessionOrEmpty(Long userId) {
        return sessionRepository.findByUserIdAndEndedAtIsNull(userId)
                .map(SessionResponse.Detail::from);
    }

    private StudySession getSessionByUser(Long userId, Long sessionId) {
        StudySession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("세션을 찾을 수 없습니다."));

        if(!session.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("접근 권한이 없습니다.");
        }

        return session;
    }

    private void validateSessionNotEnded(StudySession session) {
        if(session.getEndedAt() != null) {
            throw new IllegalStateException("이미 종료된 세션입니다.");
        }
    }

    @Transactional  // readOnly 아님: 처음 보는 앱/도메인 표시 이름을 캐시에 저장할 수 있음
    public List<SessionResponse.LogSummaryItem> getLogSummary(Long userId, Long sessionId) {
        StudySession session = getSessionByUser(userId, sessionId);

        Map<String, Integer> durations = buildDurationMap(sessionId);

        List<SessionResponse.LogSummaryItem> result = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : durations.entrySet()) {
            String[] parts = entry.getKey().split("::", 2);
            String logType = parts[0];
            String logValue = parts[1];
            int totalSec = entry.getValue();

            String defaultCategory;
            if ("APP".equals(logType)) {
                defaultCategory = classificationService.classifyApp(
                        userId, logValue, false, session.getStudyType());
            } else {
                defaultCategory = classificationService.classifyDomain(userId, logValue);
            }

            // IDLE은 팝업에 표시 안 함
            if ("IDLE".equals(defaultCategory)) continue;

            result.add(SessionResponse.LogSummaryItem.builder()
                    .logType(logType)
                    .logValue(logValue)
                    .totalSec(totalSec)
                    .category(defaultCategory)
                    .build());
        }

        // 완료 팝업은 처음 보는 앱/도메인도 AI로 이름 정리 (useAi = true)
        Map<String, String> names = appDisplayNameService.resolve(
                result.stream().map(SessionResponse.LogSummaryItem::getLogValue).toList(), true);
        List<SessionResponse.LogSummaryItem> named = result.stream()
                .map(i -> SessionResponse.LogSummaryItem.builder()
                        .logType(i.getLogType())
                        .logValue(i.getLogValue())
                        .displayName(names.get(i.getLogValue()))
                        .totalSec(i.getTotalSec())
                        .category(i.getCategory())
                        .build())
                .sorted((a, b) -> b.getTotalSec() - a.getTotalSec())
                .toList();
        return named;
    }

    @Transactional
    public SessionResponse.Detail finalizeSession(Long userId, Long sessionId,
                                                  SessionRequest.Finalize request) {
        StudySession session = getSessionByUser(userId, sessionId);

        for (SessionRequest.LogNoteItem note : request.getNotes()) {
            if ("STUDY".equals(note.getCategory())) {
                if (note.getMemo() == null || note.getMemo().trim().length() < 5) {
                    throw new IllegalArgumentException(
                            "공부로 분류된 항목은 5자 이상의 메모가 필요합니다: " + note.getLogValue());
                }
            }
        }

        // 기존 노트 삭제 후 재저장 (재시도 시 중복 방지)
        sessionLogNoteRepository.deleteBySessionId(sessionId);

        int studySec = 0;
        int distractSec = 0;
        int neutralSec = 0;

        Map<String, Integer> durations = buildDurationMap(sessionId);
        List<SessionLogNote> notes = new ArrayList<>();
        for (SessionRequest.LogNoteItem item : request.getNotes()) {
            int totalSec = durations.getOrDefault(item.getLogType() + "::" + item.getLogValue(), 0);

            notes.add(SessionLogNote.builder()
                    .session(session)
                    .logType(item.getLogType())
                    .logValue(item.getLogValue())
                    .category(item.getCategory())
                    .memo(item.getMemo() != null ? item.getMemo().trim() : null)
                    .build());

            if ("STUDY".equals(item.getCategory())) studySec += totalSec;
            else if ("DISTRACT".equals(item.getCategory())) distractSec += totalSec;
            else if ("NEUTRAL".equals(item.getCategory())) neutralSec += totalSec;
        }

        sessionLogNoteRepository.saveAll(notes);
        session.updateStudySec(studySec, distractSec, neutralSec);

        return SessionResponse.Detail.from(session);
    }
}
