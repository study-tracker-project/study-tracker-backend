-- ========================================
-- Study Tracker Schema v2
-- ========================================

-- 1. 사용자
CREATE TABLE IF NOT EXISTS users (
     id                  BIGINT          NOT NULL AUTO_INCREMENT,
     email               VARCHAR(255)    NOT NULL,
    name                VARCHAR(100)    NOT NULL,
    google_id           VARCHAR(255)    NOT NULL,
    day_change_hour     TINYINT         NOT NULL DEFAULT 5,
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email),
    UNIQUE KEY uk_users_google_id (google_id)
    );

-- 2. 기기
CREATE TABLE IF NOT EXISTS devices (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    user_id         BIGINT          NOT NULL,
    device_name     VARCHAR(100)    NOT NULL,
    device_type     VARCHAR(10)     NOT NULL COMMENT 'PC / IOS / IPAD / ANDROID',
    device_token    VARCHAR(255)    NOT NULL,
    push_token      VARCHAR(500),
    last_seen       DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_devices_token (device_token),
    FOREIGN KEY (user_id) REFERENCES users (id)
    );

-- 3. 공부 세션
CREATE TABLE IF NOT EXISTS study_sessions (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    user_id         BIGINT          NOT NULL,
    study_type      VARCHAR(10)     NOT NULL COMMENT 'OFFLINE / ONLINE',
    started_at      DATETIME        NOT NULL,
    ended_at        DATETIME,
    target_sec      INT,
    is_auto_ended   TINYINT(1)      NOT NULL DEFAULT 0,
    total_sec       INT             NOT NULL DEFAULT 0,
    study_sec       INT             NOT NULL DEFAULT 0,
    distract_sec    INT             NOT NULL DEFAULT 0,
    neutral_sec     INT             NOT NULL DEFAULT 0,
    pause_sec       INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_session_user_time (user_id, started_at),
    FOREIGN KEY (user_id) REFERENCES users (id)
    );

-- 4. 앱 활동 로그 (PC 에이전트)
CREATE TABLE IF NOT EXISTS activity_logs (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    session_id      BIGINT          NOT NULL,
    device_id       BIGINT          NOT NULL,
    app_name        VARCHAR(255)    NOT NULL,
    window_title    VARCHAR(500),
    started_at      DATETIME        NOT NULL,
    duration_sec    INT             NOT NULL DEFAULT 0,
    is_idle         TINYINT(1)      NOT NULL DEFAULT 0,
    category        VARCHAR(10)     NOT NULL DEFAULT 'NEUTRAL'
    COMMENT 'STUDY / DISTRACT / NEUTRAL / IDLE',
    PRIMARY KEY (id),
    INDEX idx_activity_session (session_id),
    INDEX idx_activity_device_time (device_id, started_at),
    INDEX idx_activity_started_at (started_at),
    FOREIGN KEY (session_id) REFERENCES study_sessions (id),
    FOREIGN KEY (device_id) REFERENCES devices (id)
    );

-- 5. 브라우저 로그 (Chrome Extension, PC 전용)
CREATE TABLE IF NOT EXISTS browser_logs (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    session_id      BIGINT          NOT NULL,
    device_id       BIGINT          NOT NULL,
    domain          VARCHAR(255)    NOT NULL,
    page_title      VARCHAR(500),
    started_at      DATETIME        NOT NULL,
    duration_sec    INT             NOT NULL DEFAULT 0,
    category        VARCHAR(10)     NOT NULL DEFAULT 'NEUTRAL',
    PRIMARY KEY (id),
    INDEX idx_browser_session (session_id),
    INDEX idx_browser_domain (domain),
    INDEX idx_browser_started_at (started_at),
    FOREIGN KEY (session_id) REFERENCES study_sessions (id),
    FOREIGN KEY (device_id) REFERENCES devices (id)
    );

-- 6. 사용자 분류 커스터마이즈
CREATE TABLE IF NOT EXISTS app_classifications (
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    user_id     BIGINT          NOT NULL,
    type        VARCHAR(10)     NOT NULL COMMENT 'DOMAIN / APP',
    value       VARCHAR(255)    NOT NULL,
    category    VARCHAR(10)     NOT NULL COMMENT 'STUDY / DISTRACT / NEUTRAL',
    PRIMARY KEY (id),
    UNIQUE KEY uk_classification (user_id, type, value),
    FOREIGN KEY (user_id) REFERENCES users (id)
    );

-- 7. 세션 로그 메모 (팝업 완료 시 저장)
CREATE TABLE IF NOT EXISTS session_log_notes (
                                                 id          BIGINT          NOT NULL AUTO_INCREMENT,
                                                 session_id  BIGINT          NOT NULL,
                                                 log_type    VARCHAR(10)     NOT NULL COMMENT 'APP / DOMAIN',
                                                 log_value   VARCHAR(255)    NOT NULL COMMENT '예: idea64.exe / youtube.com',
                                                 category    VARCHAR(10)     NOT NULL COMMENT 'STUDY / DISTRACT / NEUTRAL',
                                                 memo        VARCHAR(500),
                                                 PRIMARY KEY (id),
                                                 INDEX idx_note_session (session_id),
                                                 FOREIGN KEY (session_id) REFERENCES study_sessions (id)
);

-- 8. 앱/도메인 표시 이름 캐시 (raw 실행 파일명/도메인 -> 사람이 읽을 이름)
--    처음 보는 값만 AI(Claude Haiku)로 정리하고 여기 저장해서 재사용한다.
CREATE TABLE IF NOT EXISTS app_display_names (
    raw_value       VARCHAR(255)    NOT NULL,
    display_name    VARCHAR(255)    NOT NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (raw_value)
    );

-- 자주 쓰는 앱은 미리 채워둬서 AI 호출 없이 바로 뜨게 한다.
INSERT IGNORE INTO app_display_names (raw_value, display_name) VALUES
    ('code.exe', 'Visual Studio Code'), ('idea64.exe', 'IntelliJ IDEA'),
    ('devenv.exe', 'Visual Studio'), ('notion.exe', 'Notion'),
    ('obsidian.exe', 'Obsidian'), ('pycharm64.exe', 'PyCharm'),
    ('webstorm64.exe', 'WebStorm'), ('datagrip64.exe', 'DataGrip'),
    ('kakaotalk.exe', '카카오톡'), ('discord.exe', 'Discord'),
    ('steam.exe', 'Steam'), ('spotify.exe', 'Spotify'),
    ('youtubemusic.exe', 'YouTube Music'), ('clock.exe', '시계'),
    ('calculator.exe', '계산기'), ('windowsterminal.exe', 'Windows Terminal'),
    ('explorer.exe', '파일 탐색기'), ('chrome.exe', 'Chrome'),
    ('msedge.exe', 'Microsoft Edge'), ('firefox.exe', 'Firefox'),
    ('whale.exe', '웨일'), ('notepad.exe', '메모장'),
    ('notepad++.exe', 'Notepad++'), ('powerpnt.exe', 'PowerPoint'),
    ('winword.exe', 'Word'), ('excel.exe', 'Excel'),
    ('onenote.exe', 'OneNote'), ('slack.exe', 'Slack'),
    ('teams.exe', 'Teams'), ('zoom.exe', 'Zoom');