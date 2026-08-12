package com.aivle.backend.techops.controller;

import static com.aivle.backend.techops.dto.TechOpsAdvisoryModels.*;
import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.common.security.CurrentUserProvider;
import com.aivle.backend.techops.service.TechOpsAdvisoryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v3/projects/{projectId}/tech-ops/advisory")
@RequiredArgsConstructor
@Slf4j
public class TechOpsAdvisoryController {
    private final TechOpsAdvisoryService service;
    private final CurrentUserProvider user;
    @PostMapping
    public ApiResponse<AdvisoryResponse> generate(@PathVariable Long projectId, @Valid @RequestBody AdvisoryRequest body,
            HttpServletRequest request) {
        return ApiResponse.success(service.generate(user.currentUserId(), projectId, body), request.getHeader("X-Request-Id"));
    }
    @PostMapping("/run")
    public ApiResponse<AdvisoryResponse> run(@PathVariable Long projectId, HttpServletRequest request) {
        Long userId = user.currentUserId();
        log.info("Tech-ops advisory run requested: projectId={}, userId={}, requestId={}",
            projectId, userId, request.getHeader("X-Request-Id"));
        return ApiResponse.success(service.generateFromProject(userId, projectId), request.getHeader("X-Request-Id"));
    }
}
