package com.wonbin.study_tracker.domain.classification.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wonbin.study_tracker.domain.classification.entity.AppDisplayName;
import com.wonbin.study_tracker.domain.classification.repository.AppDisplayNameRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.*;
import java.util.stream.Collectors;

/**
 * raw 실행 파일명/도메인(chrome.exe, idea64.exe, inflearn.com ...)을 사람이
 * 읽을 수 있는 이름으로 바꿔준다. 캐시(app_display_names)에 있으면 그대로 쓰고,
 * 처음 보는 값만 Claude Haiku로 한 번 정리해서 캐시에 저장한다 —
 * 사용자가 쓰는 프로그램은 종류가 한정적이라 실제 AI 호출은 초반에만 일어난다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AppDisplayNameService {

    private static final String ANTHROPIC_URL = "https://api.anthropic.com/v1/messages";
    private static final String MODEL = "claude-haiku-4-5";

    private final AppDisplayNameRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestClient restClient = RestClient.create();

    @Value("${anthropic.api-key:}")
    private String anthropicApiKey;

    public Map<String, String> resolve(Collection<String> rawValues) {
        return resolve(rawValues, false);
    }

    /**
     * @param useAi 처음 보는 값을 AI로 정리할지. 완료 팝업(getLogSummary)에서만 true로 호출.
     *              통계 화면 등 자주 불리는 경로는 false로 두고 캐시/휴리스틱만 쓴다.
     */
    public Map<String, String> resolve(Collection<String> rawValues, boolean useAi) {
        Set<String> distinct = rawValues.stream()
                .filter(v -> v != null && !v.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (distinct.isEmpty()) return Map.of();

        Map<String, String> result = new HashMap<>();
        Set<String> misses = new LinkedHashSet<>();

        for (AppDisplayName cached : repository.findAllById(distinct)) {
            result.put(cached.getRawValue(), cached.getDisplayName());
        }
        for (String raw : distinct) {
            if (!result.containsKey(raw)) {
                result.put(raw, heuristic(raw));
                misses.add(raw);
            }
        }

        if (!misses.isEmpty() && useAi && !anthropicApiKey.isBlank()) {
            aiResolve(misses).forEach((raw, name) -> {
                if (name != null && !name.isBlank()) {
                    result.put(raw, name);
                    trySave(raw, name);
                }
            });
        }
        return result;
    }

    // 확장자만 떼고 구분자를 공백으로 바꿔 첫 글자만 대문자로. 도메인은 www.만 떼고 그대로.
    static String heuristic(String raw) {
        String v = raw;
        if (v.toLowerCase().endsWith(".exe")) {
            v = v.substring(0, v.length() - 4);
            String[] parts = v.split("[_\\-.]+");
            StringBuilder sb = new StringBuilder();
            for (String p : parts) {
                if (p.isEmpty()) continue;
                if (sb.length() > 0) sb.append(' ');
                sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
            }
            return sb.length() > 0 ? sb.toString() : raw;
        }
        return v.startsWith("www.") ? v.substring(4) : v;
    }

    private Map<String, String> aiResolve(Set<String> values) {
        try {
            String prompt = """
                    아래는 Windows 실행 파일명 또는 웹 도메인 목록이다. 각각을 사람이 알아볼 수 있는
                    짧은 이름으로 바꿔라. 실행 파일명은 통용되는 프로그램 이름으로(idea64.exe -> IntelliJ IDEA),
                    도메인은 그 서비스 이름으로(inflearn.com -> 인프런). 모르면 확장자만 뗀 값을 그대로 써라.
                    JSON 객체 하나만 출력한다. 키는 입력값 그대로, 값은 표시 이름.

                    입력: %s
                    """.formatted(objectMapper.writeValueAsString(values));

            Map<String, Object> body = Map.of(
                    "model", MODEL,
                    "max_tokens", 1024,
                    "messages", List.of(Map.of("role", "user", "content", prompt))
            );

            JsonNode resp = restClient.post()
                    .uri(ANTHROPIC_URL)
                    .header("x-api-key", anthropicApiKey)
                    .header("anthropic-version", "2023-06-01")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            String text = resp.path("content").path(0).path("text").asText("");
            text = text.replaceAll("(?s)```(json)?", "").trim();
            JsonNode parsed = objectMapper.readTree(text);

            Map<String, String> out = new HashMap<>();
            parsed.fields().forEachRemaining(e -> out.put(e.getKey(), e.getValue().asText()));
            return out;
        } catch (Exception e) {
            log.warn("표시 이름 AI 정리 실패, 휴리스틱으로 대체: {}", e.getMessage());
            return Map.of();
        }
    }

    private void trySave(String raw, String name) {
        try {
            repository.save(new AppDisplayName(raw, name));
        } catch (RuntimeException e) {
            // 동시 요청이 먼저 저장한 경우 등 — 무시
        }
    }
}
