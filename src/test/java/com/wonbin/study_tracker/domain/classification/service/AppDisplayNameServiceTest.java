package com.wonbin.study_tracker.domain.classification.service;

import com.wonbin.study_tracker.domain.classification.entity.AppDisplayName;
import com.wonbin.study_tracker.domain.classification.repository.AppDisplayNameRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppDisplayNameServiceTest {

    @Mock
    private AppDisplayNameRepository repository;

    @InjectMocks
    private AppDisplayNameService service;

    @Test
    void heuristic은_exe_확장자를_떼고_첫글자를_대문자로() {
        assertThat(AppDisplayNameService.heuristic("some_editor.exe")).isEqualTo("Some Editor");
        assertThat(AppDisplayNameService.heuristic("Foo-Bar.exe")).isEqualTo("Foo Bar");
    }

    @Test
    void heuristic은_도메인은_www만_떼고_그대로() {
        assertThat(AppDisplayNameService.heuristic("www.example.com")).isEqualTo("example.com");
        assertThat(AppDisplayNameService.heuristic("inflearn.com")).isEqualTo("inflearn.com");
    }

    @Test
    void resolve는_캐시에_있으면_그_이름을_없으면_휴리스틱을_쓴다() {
        when(repository.findAllById(any()))
                .thenReturn(List.of(new AppDisplayName("idea64.exe", "IntelliJ IDEA")));

        Map<String, String> result = service.resolve(List.of("idea64.exe", "weird.exe"));

        assertThat(result.get("idea64.exe")).isEqualTo("IntelliJ IDEA");
        assertThat(result.get("weird.exe")).isEqualTo("Weird");
    }

    @Test
    void resolve는_API_키가_없으면_AI를_부르지_않고_캐시에도_안_쓴다() {
        when(repository.findAllById(any())).thenReturn(List.of());

        Map<String, String> result = service.resolve(List.of("unknown.exe"), true);

        assertThat(result.get("unknown.exe")).isEqualTo("Unknown");
        verify(repository, never()).save(any());
    }
}
