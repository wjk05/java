package com.ruici.ai.modules.simulation.service;

import com.ruici.ai.common.ai.LlmProviderRegistry;
import com.ruici.ai.common.exception.BusinessException;
import com.ruici.ai.infrastructure.redis.InterviewSessionCache;
import com.ruici.ai.modules.document.model.ResumeEntity;
import com.ruici.ai.modules.document.service.ResumePersistenceService;
import com.ruici.ai.modules.simulation.listener.EvaluateStreamProducer;
import com.ruici.ai.modules.simulation.model.CreateInterviewRequest;
import com.ruici.ai.modules.simulation.model.HistoricalQuestion;
import com.ruici.ai.modules.simulation.model.InterviewQuestionDTO;
import com.ruici.ai.modules.simulation.model.InterviewSessionDTO;
import com.ruici.ai.modules.simulation.model.SimulationDifficulty;
import com.ruici.ai.modules.simulation.model.SimulationDirection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("情景模拟会话服务测试")
class InterviewSessionServiceTest {

    @Mock
    private InterviewQuestionService questionService;

    @Mock
    private AnswerEvaluationService evaluationService;

    @Mock
    private InterviewPersistenceService persistenceService;

    @Mock
    private InterviewSessionCache sessionCache;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private EvaluateStreamProducer evaluateStreamProducer;

    @Mock
    private LlmProviderRegistry llmProviderRegistry;

    @Mock
    private ResumePersistenceService resumePersistenceService;

    @Mock
    private ChatClient chatClient;

    @InjectMocks
    private InterviewSessionService interviewSessionService;

    @Nested
    @DisplayName("主问题计数")
    class MainQuestionCounting {

        @Test
        @DisplayName("只统计主问题，不把追问算入总题数")
        void shouldCountOnlyMainQuestions() {
            List<InterviewQuestionDTO> questions = List.of(
                InterviewQuestionDTO.create(0, "主问题1", "GENERAL", "综合能力", "topic-1", false, null),
                InterviewQuestionDTO.create(1, "追问1-1", "GENERAL", "综合能力-追问1", null, true, 0),
                InterviewQuestionDTO.create(2, "主问题2", "GENERAL", "综合能力", "topic-2", false, null),
                InterviewQuestionDTO.create(3, "追问2-1", "GENERAL", "综合能力-追问1", null, true, 2),
                InterviewQuestionDTO.create(4, "主问题3", "GENERAL", "综合能力", "topic-3", false, null)
            );

            assertThat(InterviewSessionService.countMainQuestions(questions)).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("简历文本校验")
    class ResumeTextValidation {

        @Test
        @DisplayName("有效的履历文本允许继续创建会话")
        void shouldAcceptMeaningfulResumeText() {
            String resumeText = "张三\n五年前端开发经验，负责多个项目交付。\n"
                + "工作经历：主导后台管理系统重构，优化性能与可维护性。\n"
                + "项目经历：负责招聘平台、数据报表与跨团队协作。\n"
                + "技能：Java、Spring Boot、React、PostgreSQL。";

            assertThatCode(() -> InterviewSessionService.validateResumeTextUsable(resumeText))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("过短文档拒绝作为基于简历的出题素材")
        void shouldRejectShortResumeText() {
            assertThatThrownBy(() -> InterviewSessionService.validateResumeTextUsable("太短了"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("内容过短");
        }

        @Test
        @DisplayName("没有履历信号的噪声文本拒绝继续")
        void shouldRejectTextWithoutResumeSignals() {
            String noisyText = "天气真好，今天适合散步和拍照。"
                + "这里没有任何履历结构，只是普通随笔与旅行记录。"
                + "为了避免长度过短，这里再补一些日常描述，但依然不是个人背景材料。"
                + "文字继续延长，用来模拟一段很长但和履历毫无关系的生活感想、旅行碎片与天气记录。";

            assertThatThrownBy(() -> InterviewSessionService.validateResumeTextUsable(noisyText))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不像可用于面试的问题素材");
        }
    }

    @Nested
    @DisplayName("基于文档创建会话")
    class DocumentBasedSessionCreation {

        @Test
        @DisplayName("仅提供 documentId 时会回填正文并透传 2.0 字段")
        void shouldResolveResumeTextFromDocumentAndPropagateCompatibilityFields() {
            Long resumeId = 123L;
            String resolvedResumeText = "张三\n五年 Java 后端经验，负责高并发交易系统、缓存治理与链路优化。"
                + "\n工作经历：主导订单履约链路重构，优化接口响应与稳定性，推动告警与压测体系落地。"
                + "\n项目经历：负责 Redis + Caffeine 多级缓存、异步编排和慢查询治理，持续优化核心链路 RT 与可维护性。"
                + "\n技能：Java、Spring Boot、Redis、PostgreSQL、消息队列、链路追踪与压测分析。";
            ResumeEntity resume = new ResumeEntity();
            resume.setId(resumeId);
            resume.setResumeText(resolvedResumeText);

            List<InterviewQuestionDTO> generatedQuestions = List.of(
                InterviewQuestionDTO.create(0, "请介绍你做过的高并发项目", "GENERAL", "项目深挖", "topic-1", false, null),
                InterviewQuestionDTO.create(1, "这个项目里最难的性能瓶颈是什么？", "GENERAL", "项目深挖-追问", null, true, 0)
            );

            CreateInterviewRequest request = new CreateInterviewRequest(
                SimulationDirection.JOB_INTERVIEW,
                null,
                SimulationDifficulty.SHARP,
                null,
                6,
                resumeId,
                true,
                true,
                "third-party",
                "java-backend",
                "senior",
                null,
                null
            );

            given(resumePersistenceService.findById(resumeId)).willReturn(Optional.of(resume));
            given(persistenceService.getHistoricalQuestions(anyString(), anyString(), eq(resumeId)))
                .willReturn(List.<HistoricalQuestion>of());
            given(llmProviderRegistry.getChatClientOrDefault("third-party")).willReturn(chatClient);
            given(questionService.generateQuestionsBySkill(
                eq(chatClient),
                any(),
                eq("java-backend"),
                eq("senior"),
                eq(resolvedResumeText),
                eq(6),
                anyList(),
                eq(null),
                eq(null)
            )).willReturn(generatedQuestions);

            InterviewSessionDTO response = interviewSessionService.createSession(request);

            assertThat(response.resumeId()).isEqualTo(resumeId);
            assertThat(response.resumeText()).isEqualTo(resolvedResumeText);
            assertThat(response.basedOnDocument()).isTrue();
            assertThat(response.simulationDirection()).isEqualTo(SimulationDirection.JOB_INTERVIEW.name());
            assertThat(response.simulationDifficulty()).isEqualTo(SimulationDifficulty.SHARP.name());
            assertThat(response.totalQuestions()).isEqualTo(1);

            ArgumentCaptor<String> sessionIdCaptor = ArgumentCaptor.forClass(String.class);
            verify(sessionCache).saveSession(
                sessionIdCaptor.capture(),
                eq(SimulationDirection.JOB_INTERVIEW.name()),
                eq("job-interview"),
                eq("java-backend"),
                eq("senior"),
                eq(SimulationDifficulty.SHARP.name()),
                eq(resolvedResumeText),
                eq(resumeId),
                eq(true),
                eq(generatedQuestions),
                eq(0),
                eq(InterviewSessionDTO.SessionStatus.CREATED)
            );

            verify(persistenceService).saveSession(
                eq(sessionIdCaptor.getValue()),
                eq("job-interview"),
                eq(resumeId),
                eq(1),
                eq(generatedQuestions),
                eq("third-party"),
                eq("java-backend"),
                eq("senior"),
                eq(SimulationDirection.JOB_INTERVIEW.name()),
                eq(SimulationDifficulty.SHARP.name()),
                eq(true)
            );
        }

        @Test
        @DisplayName("基于文档创建但缺少可用正文时快速失败")
        void shouldFailFastWhenDocumentContentIsUnavailable() {
            Long resumeId = 456L;
            ResumeEntity resume = new ResumeEntity();
            resume.setId(resumeId);
            resume.setResumeText("   ");

            CreateInterviewRequest request = new CreateInterviewRequest(
                SimulationDirection.JOB_INTERVIEW,
                null,
                SimulationDifficulty.NORMAL,
                null,
                6,
                resumeId,
                true,
                true,
                "third-party",
                "java-backend",
                "mid",
                null,
                null
            );

            given(resumePersistenceService.findById(resumeId)).willReturn(Optional.of(resume));

            assertThatThrownBy(() -> interviewSessionService.createSession(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缺少可用正文");
        }
    }
}
