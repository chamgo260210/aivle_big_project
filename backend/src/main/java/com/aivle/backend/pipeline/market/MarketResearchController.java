package com.aivle.backend.pipeline.market;

import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.common.security.CurrentUserProvider;
import com.aivle.backend.common.web.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

/**
 * Project Shell의 Market Research와 Business Model Product API.
 *
 * <p>둘 다 <b>202 로 즉시 돌려주고</b> Job/Project SSE 뒤 {@code /current} 를 재조회한다.
 * 1단계는 90~266초라 동기로 줄 방법이 없다.
 */
@RestController
@RequestMapping("/api/v3/projects/{projectId}")
@RequiredArgsConstructor
public class MarketResearchController {

    private final MarketResearchService service;
    private final CurrentUserProvider currentUser;

    /** Market 실행. current authoritative Concept 스냅샷 직렬화는 서버가 한다. */
    @PostMapping("/market-research")
    public ResponseEntity<ApiResponse<MarketResearchService.RunView>> startFull(
            @PathVariable Long projectId, @Valid @RequestBody StartRequest body,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(
            service.startFull(currentUser.currentUserId(), projectId,
                body == null ? null : body.asOf(),
                request.getHeader("Idempotency-Key"), id(request)), id(request)));
    }

    @GetMapping("/market-research/current")
    public ApiResponse<MarketResearchService.CurrentView> currentFull(
            @PathVariable Long projectId, HttpServletRequest request) {
        return ApiResponse.success(service.current(currentUser.currentUserId(), projectId,
            MarketResearchRun.Kind.FULL), id(request));
    }

    // ⚠ 사업 검증(`POST /business-validation`, 한 실행에 FULL+BM)은 **여기에 없다.**
    //   여정 2번은 main 의 두 실행(`/market-research` → `/business-model`)을 그대로 쓴다.
    //   옛 구현은 main 이 지운 `MarketAnalysisSeedLookup`·`ResearchConceptFactory` 위에
    //   서 있어서 옮겨 붙일 수가 없다 — main 자료구조(`readySelection` + 씨앗 스냅샷)에
    //   맞춰 **다시 써야** 한다. 화면이 부르지 않는 경로를 지금 다시 쓰지 않는다.
    //   남아 있는 것: `TaskType.BUSINESS_VALIDATION` · `BusinessValidationWorker` ·
    //   `MarketResearchRun.Kind.VALIDATION` · `ai/app/validation/runner.py`.
    //   붙이려면 이 넷 위에 `startValidation` + `MarketResearchInputFactory.validation()` 만
    //   새로 쓰면 된다.

    @PostMapping("/market-research/recollect")
    public ResponseEntity<ApiResponse<MarketResearchService.RunView>> recollect(
            @PathVariable Long projectId, @Valid @RequestBody RecollectRequest body,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(
            service.startRecollect(currentUser.currentUserId(), projectId,
                body.sourceMarketResearchVersionId(), body.slots(), body.from(), body.slotsFrom(), body.asOf(),
                request.getHeader("Idempotency-Key"), id(request)), id(request)));
    }

    @GetMapping("/market-research/competitor-seeds")
    public ApiResponse<ResearchCompetitorSeedService.SeedsView> currentCompetitorSeeds(
            @PathVariable Long projectId, HttpServletRequest request) {
        return ApiResponse.success(service.currentCompetitorSeeds(currentUser.currentUserId(), projectId), id(request));
    }

    @PutMapping("/market-research/competitor-seeds")
    public ApiResponse<ResearchCompetitorSeedService.SeedsView> saveCompetitorSeeds(
            @PathVariable Long projectId, @RequestBody JsonNode body, HttpServletRequest request) {
        return ApiResponse.success(service.saveCompetitorSeeds(currentUser.currentUserId(), projectId, body), id(request));
    }

    /** 정확한 MarketResearchVersion을 근거로 BM 캔버스를 만든다. */
    @PostMapping("/business-model")
    public ResponseEntity<ApiResponse<MarketResearchService.RunView>> startBm(
            @PathVariable Long projectId, @RequestBody(required = false) BmRequest body,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(
            service.startBm(currentUser.currentUserId(), projectId,
                request.getHeader("Idempotency-Key"), id(request)),
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

    /** 공식 요청은 기준일만 받고 Concept authority는 서버가 결정한다. */
    public record StartRequest(String asOf) { }

    public record RecollectRequest(
        @jakarta.validation.constraints.NotNull Long sourceMarketResearchVersionId,
        String slots, String from, String slotsFrom, String asOf) { }

    /** BM source는 서버가 current immutable Market version에서 결속한다. */
    public record BmRequest() { }

    // ⚠ `ValidationRequest` 는 사업 검증 엔드포인트와 함께 뺐다(위 주석 참조).
    //   되살릴 때 기억할 것: 이 record 에 `conceptId` 를 **넣지 말 것.** 사업안을 확정하기
    //   전에 누르면 서버가 견본으로 조용히 떨어져 남의 컨셉 원장으로 6/6 SUCCEEDED 를
    //   냈다(2026-08-12 실측). 이름표는 확정된 시드에서 서버가 정한다.

    /**
     * ⚠ 두 칸 모두 <b>필수가 아니다.</b> 전부 선택 입력이므로 빈 계획도 정상 요청이고,
     * 그때 캔버스는 그만큼 빈 채로 나온다 — 그 사실을 화면이 제출 전에 확인받는다.
     */
    public record PlanRequest(JsonNode plan, JsonNode constraints) { }

    private String id(HttpServletRequest request) {
        return RequestIds.resolve(request);
    }
}
