package com.wonbin.study_tracker.domain.session.service;

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

        return sessionLogNoteRepository.findBySessionId(sessionId).stream()
                .map(SessionResponse.LogNote::from)
                .toList();
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

    @Transactional(readOnly = true)
    public List<SessionResponse.LogSummaryItem> getLogSummary(Long userId, Long sessionId) {
        StudySession session = getSessionByUser(userId, sessionId);

        Map<String, int[]> summaryMap = new LinkedHashMap<>();

        List<ActivityLog> activityLogs = activityLogRepository.findBySessionId(sessionId);
        for (ActivityLog log : activityLogs) {
            String key = "APP::" + log.getAppName();
            summaryMap.computeIfAbsent(key, k -> new int[]{0});
            summaryMap.get(key)[0] += log.getDurationSec();
        }

        List<BrowserLog> browserLogs = browserLogRepository.findBySessionId(sessionId);
        for (BrowserLog log : browserLogs) {
            String key = "DOMAIN::" + log.getDomain();
            summaryMap.computeIfAbsent(key, k -> new int[]{0});
            summaryMap.get(key)[0] += log.getDurationSec();
        }

        List<SessionResponse.LogSummaryItem> result = new ArrayList<>();
        for (Map.Entry<String, int[]> entry : summaryMap.entrySet()) {
            String[] parts = entry.getKey().split("::");
            String logType = parts[0];
            String logValue = parts[1];
            int totalSec = entry.getValue()[0];

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

        result.sort((a, b) -> b.getTotalSec() - a.getTotalSec());
        return result;
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

        List<SessionLogNote> notes = new ArrayList<>();
        for (SessionRequest.LogNoteItem item : request.getNotes()) {
            notes.add(SessionLogNote.builder()
                    .session(session)
                    .logType(item.getLogType())
                    .logValue(item.getLogValue())
                    .category(item.getCategory())
                    .memo(item.getMemo() != null ? item.getMemo().trim() : null)
                    .build());

            int totalSec = 0;
            if ("APP".equals(item.getLogType())) {
                totalSec = activityLogRepository.findBySessionId(sessionId).stream()
                        .filter(l -> l.getAppName().equals(item.getLogValue()))
                        .mapToInt(ActivityLog::getDurationSec).sum();
            } else {
                totalSec = browserLogRepository.findBySessionId(sessionId).stream()
                        .filter(l -> l.getDomain().equals(item.getLogValue()))
                        .mapToInt(BrowserLog::getDurationSec).sum();
            }

            if ("STUDY".equals(item.getCategory())) studySec += totalSec;
            else if ("DISTRACT".equals(item.getCategory())) distractSec += totalSec;
            else if ("NEUTRAL".equals(item.getCategory())) neutralSec += totalSec;
        }

        sessionLogNoteRepository.saveAll(notes);
        session.updateStudySec(studySec, distractSec, neutralSec);

        return SessionResponse.Detail.from(session);
    }
}
