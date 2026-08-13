package com.aivle.backend.journey;

import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.common.security.CurrentUserProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

/**
 * 여정 — 재무 → 「시장 인터뷰」 → 마케팅.
 *
 * <p>202 로 즉시 돌려주고 화면이 {@code /current} 를 폴링한다. n=80 이면 수집 80셀 +
 * 주제 코딩 1회라 동기로 줄 수 있는 시간이 아니다.
 */
@RestController
@RequestMapping("/api/v2/projects/{projectId}")
@RequiredArgsConstructor
public class MarketInterviewController {

    private final MarketInterviewService service;
    private final MarketInterviewBoardService boards;
    private final CurrentUserProvider currentUser;

    /**
     * 컨셉보드 — 확정된 사업안에서 여섯 칸을 꺼내 준다. <b>동기 200 · LLM 0회</b>다.
     * 지어낼 것이 없으므로 결정론적 추출이고, TaskRun 도 남기지 않는다.
     *
     * <p>확정된 사업안이 없으면 404 다. 견본으로 떨어지지 않는다 — 조용한 기본값이
     * 실제로 사고를 냈다.
     */
    @GetMapping("/market-interview/board")
    public ApiResponse<JsonNode> board(@PathVariable Long projectId, HttpServletRequest request) {
        return ApiResponse.success(boards.board(currentUser.currentUserId(), projectId), id(request));
    }

    @PostMapping("/market-interview")
    public ResponseEntity<ApiResponse<MarketInterviewService.RunView>> start(
            @PathVariable Long projectId, @Valid @RequestBody StartRequest body,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(
            service.start(currentUser.currentUserId(), projectId,
                body.conceptBoard(), body.sampleSize()), id(request)));
    }

    @GetMapping("/market-interview/current")
    public ApiResponse<MarketInterviewService.CurrentView> current(
            @PathVariable Long projectId, HttpServletRequest request) {
        return ApiResponse.success(service.current(currentUser.currentUserId(), projectId), id(request));
    }

    /**
     * {@code conceptBoard} 는 화면이 손본 여섯 칸이다. 칸 집합과 자료형은
     * {@link MarketInterviewInputFactory} 가 보내기 전에 확인한다 — AI 쪽
     * {@code extra="forbid"} 가 내는 400 은 사용자가 무엇을 잘못했는지 말해 주지 못한다.
     */
    public record StartRequest(@NotNull JsonNode conceptBoard, @NotNull Integer sampleSize) { }

    private String id(HttpServletRequest request) {
        Object value = request.getAttribute("requestId");
        return value == null ? null : value.toString();
    }
}
