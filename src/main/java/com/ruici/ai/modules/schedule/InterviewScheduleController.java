package com.ruici.ai.modules.schedule;

import com.ruici.ai.common.result.Result;
import com.ruici.ai.modules.schedule.model.CreateInterviewRequest;
import com.ruici.ai.modules.schedule.model.InterviewScheduleDTO;
import com.ruici.ai.modules.schedule.model.InterviewStatus;
import com.ruici.ai.modules.schedule.model.ParseRequest;
import com.ruici.ai.modules.schedule.model.ParseResponse;
import com.ruici.ai.modules.schedule.service.InterviewParseService;
import com.ruici.ai.modules.schedule.service.InterviewScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 场景日程管理控制器。
 *
 * <p>当前数据模型仍沿用“面试日程”命名，但接口已补充更通用的 `schedule` 路径，
 * 用于承载邀请解析、待办排期和场景化日历管理。</p>
 */
@Slf4j
@RestController
@RequestMapping({"/api/schedule", "/api/simulation-schedule"})
@RequiredArgsConstructor
public class InterviewScheduleController {

    private final InterviewScheduleService scheduleService;
    private final InterviewParseService parseService;

    /**
     * 解析场景邀约文本。
     *
     * <p>当前主要覆盖面试邀约，但保留为通用排期解析入口，
     * 后续可继续扩展到答辩、咨询、课程邀约等文本。</p>
     */
    @PostMapping("/parse")
    public Result<ParseResponse> parse(@Valid @RequestBody ParseRequest request) {
        log.info("接收到解析请求，来源: {}", request.getSource());
        ParseResponse response = parseService.parse(request.getRawText(), request.getSource());
        return Result.success(response);
    }

    /**
     * 创建场景日程记录。
     */
    @PostMapping
    public Result<InterviewScheduleDTO> create(@Valid @RequestBody CreateInterviewRequest request) {
        log.info("创建场景日程记录: {} - {}", request.getCompanyName(), request.getPosition());
        InterviewScheduleDTO dto = scheduleService.create(request);
        return Result.success(dto);
    }

    /**
     * 根据 ID 获取日程记录。
     */
    @GetMapping("/{id}")
    public Result<InterviewScheduleDTO> getById(@PathVariable Long id) {
        InterviewScheduleDTO dto = scheduleService.getById(id);
        return Result.success(dto);
    }

    /**
     * 获取日程记录列表。
     */
    @GetMapping
    public Result<List<InterviewScheduleDTO>> getAll(
        @RequestParam(required = false) String status,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end
    ) {
        List<InterviewScheduleDTO> list = scheduleService.getAll(status, start, end);
        return Result.success(list);
    }

    /**
     * 更新日程记录。
     */
    @PutMapping("/{id}")
    public Result<InterviewScheduleDTO> update(
        @PathVariable Long id,
        @Valid @RequestBody CreateInterviewRequest request
    ) {
        log.info("更新场景日程记录: ID={}", id);
        InterviewScheduleDTO dto = scheduleService.update(id, request);
        return Result.success(dto);
    }

    /**
     * 删除日程记录。
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        log.info("删除场景日程记录: ID={}", id);
        scheduleService.delete(id);
        return Result.success(null);
    }

    /**
     * 更新日程状态。
     */
    @RequestMapping(path = "/{id}/status", method = {RequestMethod.PATCH, RequestMethod.PUT})
    public Result<InterviewScheduleDTO> updateStatus(
        @PathVariable Long id,
        @RequestParam InterviewStatus status
    ) {
        log.info("更新日程状态: ID={}, status={}", id, status);
        InterviewScheduleDTO dto = scheduleService.updateStatus(id, status);
        return Result.success(dto);
    }
}
