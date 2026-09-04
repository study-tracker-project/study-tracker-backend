package com.wonbin.study_tracker.domain.log.service;


import com.wonbin.study_tracker.domain.classification.service.ClassificationService;
import com.wonbin.study_tracker.domain.device.entity.Device;
import com.wonbin.study_tracker.domain.device.repository.DeviceRepository;
import com.wonbin.study_tracker.domain.log.dto.LogRequest;
import com.wonbin.study_tracker.domain.log.entity.ActivityLog;
import com.wonbin.study_tracker.domain.log.entity.BrowserLog;
import com.wonbin.study_tracker.domain.log.repository.ActivityLogRepository;
import com.wonbin.study_tracker.domain.log.repository.BrowserLogRepository;
import com.wonbin.study_tracker.domain.session.entity.StudySession;
import com.wonbin.study_tracker.domain.session.repository.StudySessionRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LogService {

    private final ActivityLogRepository activityLogRepository;
    private final BrowserLogRepository browserLogRepository;
    private final StudySessionRepository sessionRepository;
    private final DeviceRepository deviceRepository;
    private final ClassificationService classificationService;


    @Transactional
    public int saveActivityLogs(Long userId, LogRequest.ActivityBatch request) {
        StudySession session = getSessionForLogging(request.getSessionId(), userId);
        Device device = getDevice(request.getDeviceId(), userId);

        int addedStudySec = 0;
        int addedDistractSec = 0;
        int addedNeutralSec = 0;
        int savedCount = 0;

        List<ActivityLog> logs = new ArrayList<>();
        for (LogRequest.ActivityItem item : request.getLogs()) {
            // 세션 종료 이후 발생한 로그는 무시
            if (session.getEndedAt() != null && item.getStartedAt().isAfter(session.getEndedAt())) {
                continue;
            }

            String category = classificationService.classifyApp(
                    userId, item.getAppName(), item.isIdle(), session.getStudyType()
            );

            if ("STUDY".equals(category)) addedStudySec += item.getDurationSec();
            else if ("DISTRACT".equals(category)) addedDistractSec += item.getDurationSec();
            else if ("NEUTRAL".equals(category)) addedNeutralSec += item.getDurationSec();

            logs.add(ActivityLog.builder()
                    .session(session)
                    .device(device)
                    .appName(item.getAppName())
                    .windowTitle(item.getWindowTitle())
                    .startedAt(item.getStartedAt())
                    .durationSec(item.getDurationSec())
                    .isIdle(item.isIdle())
                    .category(category)
                    .build());
            savedCount++;
        }

        activityLogRepository.saveAll(logs);

        if (addedStudySec > 0) session.addStudySec(addedStudySec);
        if (addedDistractSec > 0) session.addDistractSec(addedDistractSec);
        if (addedNeutralSec > 0) session.addNeutralSec(addedNeutralSec);

        return savedCount;
    }

    @Transactional
    public int saveBrowserLogs(Long userId, LogRequest.BrowserBatch request) {
        StudySession session = getSessionForLogging(request.getSessionId(), userId);
        Device device = getDevice(request.getDeviceId(), userId);

        int addedStudySec = 0;
        int addedDistractSec = 0;
        int addedNeutralSec = 0;
        int savedCount = 0;

        List<BrowserLog> logs = new ArrayList<>();
        for (LogRequest.BrowserItem item : request.getLogs()) {
            // 세션 종료 이후 발생한 로그는 무시
            if (session.getEndedAt() != null && item.getStartedAt().isAfter(session.getEndedAt())) {
                continue;
            }

            String category = classificationService.classifyDomain(userId, item.getDomain());

            if ("STUDY".equals(category)) addedStudySec += item.getDurationSec();
            else if ("DISTRACT".equals(category)) addedDistractSec += item.getDurationSec();
            else if ("NEUTRAL".equals(category)) addedNeutralSec += item.getDurationSec();

            logs.add(BrowserLog.builder()
                    .session(session)
                    .device(device)
                    .domain(item.getDomain())
                    .pageTitle(item.getPageTitle())
                    .startedAt(item.getStartedAt())
                    .durationSec(item.getDurationSec())
                    .category(category)
                    .build());
            savedCount++;
        }

        browserLogRepository.saveAll(logs);

        if (addedStudySec > 0) session.addStudySec(addedStudySec);
        if (addedDistractSec > 0) session.addDistractSec(addedDistractSec);
        if (addedNeutralSec > 0) session.addNeutralSec(addedNeutralSec);

        return savedCount;
    }


    private StudySession getSessionForLogging(Long sessionId, Long userId) {
        StudySession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("세션을 찾을 수 없습니다."));
        if (!session.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("접근 권한이 없습니다.");
        }
        return session;
    }

    private Device getDevice(Long deviceId, Long userId) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("기기를 찾을 수 없습니다."));

        if (!device.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("접근 권한이 없습니다.");
        }
        return device;
    }
}
