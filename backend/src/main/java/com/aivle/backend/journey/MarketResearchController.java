package com.aivle.backend.journey;

import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.common.security.CurrentUserProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

/**
 * 여정 2단계 — 시장조사 → 「다음」 → BM 캔버스.
 *
 * <p>둘 다 <b>202 로 즉시 돌려주고</b> 화면이 {@code /current} 를 폴링한다.
 * 1단계는 90~266초라 동기로 줄 방법이 없다.
 */
@RestController
@RequestMapping("/api/v2/projects/{projectId}")
@RequiredArgsConstructor
public class MarketResearchController {

    private final MarketResearchService service;
    private final CurrentUserProvider currentUser;

    /** 1단계 실행. 컨셉 스냅샷을 그대로 받는다 — 직렬화는 서버가 한다. */
    @PostMapping("/market-research")
    public ResponseEntity<ApiResponse<MarketResearchService.RunView>> startFull(
            @PathVariable Long projectId, @Valid @RequestBody StartRequest body,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(
            service.startFull(currentUser.currentUserId(), projectId,
                body.concept(), body.conceptId(), body.asOf()), id(request)));
    }

    @GetMapping("/market-research/current")
    public ApiResponse<MarketResearchService.CurrentView> currentFull(
            @PathVariable Long projectId, HttpServletRequest request) {
        return ApiResponse.success(service.current(currentUser.currentUserId(), projectId,
            MarketResearchRun.Kind.FULL), id(request));
    }

    /**
     * 경쟁 씨앗 — <b>사업안 화면이 받는 「경쟁/현재 대안」.</b>
     *
     * <p>슬롯 하네스가 F_COMP 슬롯의 subject 를 여기서 가져온다. 비워 두면 모델이 실명을
     * 지어내거나 자리표시자를 만든다(2026-08-08 실측).
     *
     * <p>⚠ 0개를 <b>막지 않는다</b> — 경고만 돌려준다. 입구계약서가 「수리 대상」으로
     * 남겨 둔 자리라(백로그 39) 하드 게이트로 굳히지 않는다.
     */
    @GetMapping("/competitor-seeds")
    public ApiResponse<ResearchCompetitorSeedService.SeedsView> currentSeeds(
            @PathVariable Long projectId, HttpServletRequest request) {
        return ApiResponse.success(
            service.currentSeeds(currentUser.currentUserId(), projectId), id(request));
    }

    /** <b>통째로 갈아 끼운다.</b> 순서가 값이라 한 줄씩 고치는 길을 만들지 않는다. */
    @PutMapping("/competitor-seeds")
    public ApiResponse<ResearchCompetitorSeedService.SeedsView> saveSeeds(
            @PathVariable Long projectId, @RequestBody JsonNode body,
            HttpServletRequest request) {
        return ApiResponse.success(
            service.saveSeeds(currentUser.currentUserId(), projectId, body), id(request));
    }

    /** 2단계 — 「다음」. 1단계 결과를 근거로 캔버스를 만든다. */
    @PostMapping("/business-model")
    public ResponseEntity<ApiResponse<MarketResearchService.RunView>> startBm(
            @PathVariable Long projectId, @Valid @RequestBody BmRequest body,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(
            service.startBm(currentUser.currentUserId(), projectId, body.asOf()),
            id(request)));
    }

    @GetMapping("/business-model/current")
    public ApiResponse<MarketResearchService.CurrentView> currentBm(
            @PathVariable Long projectId, HttpServletRequest request) {
        return ApiResponse.success(service.current(currentUser.currentUserId(), projectId,
            MarketResearchRun.Kind.BM), id(request));
    }

    /**
     * BM 앞 단계 — <b>사용자가 채우는 실행 계획.</b>
     *
     * <p>계획 4칸(활동·자원·파트너·고객 관계)은 컨셉 계약이 주지 않는 값이라 여기서 받는다.
     * 요청 바디에 실어 실행과 함께 보내지 않는 이유는 <b>새로고침에 사라지고 감사 기록도
     * 안 남기 때문</b>이다 — 저장해 두고 실행이 읽는다.
     */
    @GetMapping("/business-model/plan")
    public ApiResponse<BmPlanPreparationService.PlanView> currentPlan(
            @PathVariable Long projectId, HttpServletRequest request) {
        return ApiResponse.success(
            service.currentPlan(currentUser.currentUserId(), projectId), id(request));
    }

    @PatchMapping("/business-model/plan")
    public ApiResponse<BmPlanPreparationService.PlanView> savePlan(
            @PathVariable Long projectId, @RequestBody PlanRequest body,
            HttpServletRequest request) {
        return ApiResponse.success(service.savePlan(currentUser.currentUserId(), projectId,
            body.plan(), body.constraints()), id(request));
    }

    /** {@code conceptId} 는 AI 쪽 {@code pipeline.CONCEPTS} 의 <b>이름표</b>다. */
    /**
     * ⚠ {@code conceptId}·{@code concept} 는 <b>받되 쓰지 않는다.</b> 컨셉은 확정된
     * Market Seed 가 정하고({@link MarketResearchService#startFull}), 시드가 없으면
     * 실패한다. 필드는 요청 모양을 깨지 않으려고 남긴다 — {@link BmRequest} 와 같은 결이다.
     *
     * <p><b>{@code @NotBlank} 를 뗀 이유.</b> 화면이 보내던 값은 견본 이름표였고
     * (「beauty-noshow」), 그 값이 시드 없는 프로젝트에서 <b>미용실 견본 원장</b>을 태웠다.
     * 화면에서 견본을 걷어내면서 이제 {@code null} 이 오는데, 필수 제약이 남아 있으면
     * 그 자리에서 400 이 난다(실측). 안 쓰는 값을 필수로 두지 않는다.
     */
    public record StartRequest(String conceptId, @NotBlank String asOf, JsonNode concept) { }

    /**
     * {@code conceptId} 는 <b>받되 쓰지 않는다</b> — 2단계 컨셉은 1단계 결과에서 잇는다.
     * 클라이언트가 보낸 값으로 덮으면 「관측은 A, 잣대는 B」가 된다. 필드는 요청 모양을
     * 깨지 않으려고 남긴다.
     */
    public record BmRequest(@NotBlank String conceptId, @NotBlank String asOf) { }

    /**
     * ⚠ 두 칸 모두 <b>필수가 아니다.</b> 전부 선택 입력이므로 빈 계획도 정상 요청이고,
     * 그때 캔버스는 그만큼 빈 채로 나온다 — 그 사실을 화면이 제출 전에 확인받는다.
     */
    public record PlanRequest(JsonNode plan, JsonNode constraints) { }

    private String id(HttpServletRequest request) {
        Object value = request.getAttribute("requestId");
        return value == null ? null : value.toString();
    }
}
