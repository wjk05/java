package com.ruici.ai.modules.document.service;

import com.ruici.ai.infrastructure.file.FileHashService;
import com.ruici.ai.infrastructure.mapper.ResumeMapper;
import com.ruici.ai.modules.document.model.ResumeEntity;
import com.ruici.ai.modules.document.repository.ResumeAnalysisRepository;
import com.ruici.ai.modules.document.repository.ResumeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("文档持久化服务测试")
class ResumePersistenceServiceTest {

    @Mock
    private ResumeRepository resumeRepository;

    @Mock
    private ResumeAnalysisRepository analysisRepository;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ResumeMapper resumeMapper;

    @Mock
    private FileHashService fileHashService;

    @InjectMocks
    private ResumePersistenceService resumePersistenceService;

    @Test
    @DisplayName("获取文档列表时按上传时间倒序读取")
    void shouldLoadResumesInStableUploadedOrder() {
        ResumeEntity newest = new ResumeEntity();
        newest.setId(2L);
        newest.setOriginalFilename("newer.pdf");
        newest.setUploadedAt(LocalDateTime.of(2026, 4, 27, 10, 0));

        ResumeEntity older = new ResumeEntity();
        older.setId(1L);
        older.setOriginalFilename("older.pdf");
        older.setUploadedAt(LocalDateTime.of(2026, 4, 26, 10, 0));

        given(resumeRepository.findAllByOrderByUploadedAtDescIdDesc())
            .willReturn(List.of(newest, older));

        List<ResumeEntity> resumes = resumePersistenceService.findAllResumes();

        assertThat(resumes).containsExactly(newest, older);
        verify(resumeRepository).findAllByOrderByUploadedAtDescIdDesc();
    }
}
