package com.aivle.backend.journey;

import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.common.security.CurrentUserProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

/**
 * 여정 — 재무분석 → 「패널 트윈 조사」 → 마케팅.
 *
 * <p>202 로 즉시 돌려주고 화면이 {@code /current} 를 폴링한다. n=300 이면 분 단위라
 * 동기로 줄 방법이 없다.
 */
@RestController
@RequestMapping("/api/v2/projects/{projectId}")
@RequiredArgsConstructor
public class TwinSurveyController {

    private final TwinSurveyService service;
    private final TwinSurveyStimulusDraftService drafts;
    private final CurrentUserProvider currentUser;

    /**
     * 자극 초안. <b>동기 200</b> 이다 — 프롬프트 1회라 폴링할 것이 없고,
     * 버튼을 누른 자리에서 카드가 나와야 고르고 다듬는 흐름이 이어진다.
     *
     * <p>{@code conceptId} 는 <b>견본 컨셉 이름표</b>이고 시장조사와 같은 규율이다
     * ({@code MarketResearchController.StartRequest}) — 확정된 컨셉이 있으면
     * <b>그것이 이긴다</b>. 이름표는 컨셉 파이프라인이 아직 안 찬 환경에서 이 단계를
     * 시연·시험하기 위한 길이다.
     */
    @PostMapping("/twin-survey/stimulus-draft")
    public ApiResponse<JsonNode> stimulusDraft(@PathVariable Long projectId,
            HttpServletRequest request) {
        return ApiResponse.success(drafts.draft(currentUser.currentUserId(), projectId),
            id(request));
    }

    @PostMapping("/twin-survey")
    public ResponseEntity<ApiResponse<TwinSurveyService.RunView>> start(
            @PathVariable Long projectId, @Valid @RequestBody StartRequest body,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(
            service.start(currentUser.currentUserId(), projectId,
                body.situation(), body.pairs(), body.sampleSize()), id(request)));
    }

    @GetMapping("/twin-survey/current")
    public ApiResponse<TwinSurveyService.CurrentView> current(
            @PathVariable Long projectId, HttpServletRequest request) {
        return ApiResponse.success(service.current(currentUser.currentUserId(), projectId), id(request));
    }

    /**
     * {@code pairs} 는 계약 그대로 넘어간다 — 백엔드가 다시 가공하지 않는다.
     * 자극의 판매 가능 여부는 AI 쪽 게이트가 <b>LLM 호출 전에</b> 정한다.
     */
    public record StartRequest(@NotBlank String situation, @NotNull JsonNode pairs,
                               @NotNull Integer sampleSize) { }

    private String id(HttpServletRequest request) {
        Object value = request.getAttribute("requestId");
        return value == null ? null : value.toString();
    }
}
