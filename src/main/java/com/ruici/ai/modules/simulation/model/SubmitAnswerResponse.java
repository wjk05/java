package com.ruici.ai.modules.simulation.model;

/**
 * 提交答案响应
 */
public record SubmitAnswerResponse(
    boolean hasNextQuestion,
    InterviewQuestionDTO nextQuestion,
    int currentIndex,
    int totalQuestions
) {}
