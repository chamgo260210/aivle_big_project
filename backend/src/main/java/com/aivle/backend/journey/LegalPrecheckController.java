package com.aivle.backend.journey;

import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.common.security.CurrentUserProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v2/projects/{projectId}/legal-prechecks")
@RequiredArgsConstructor
public class LegalPrecheckController {
    private final LegalPrecheckService service; private final CurrentUserProvider currentUser;

    @PostMapping
    public ResponseEntity<ApiResponse<LegalPrecheckService.StartView>> start(@PathVariable Long projectId,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(
            service.start(currentUser.currentUserId(), projectId), id(request)));
    }
    @GetMapping("/current")
    public ApiResponse<LegalPrecheckService.CurrentView> current(@PathVariable Long projectId, HttpServletRequest request) {
        return ApiResponse.success(service.current(currentUser.currentUserId(), projectId), id(request));
    }
    @PostMapping("/answers/apply")
    public ApiResponse<IdeaOriginService.WorkspaceView> applyAnswers(@PathVariable Long projectId,
            @Valid @RequestBody ApplyAnswersRequest body, HttpServletRequest request) {
        return ApiResponse.success(service.applyAnswers(currentUser.currentUserId(), projectId,
            body.ideaOriginVersionId()), id(request));
    }
    @PostMapping("/versions/{versionId}/revision-suggestions/{index}/accept")
    public ApiResponse<IdeaOriginService.WorkspaceView> acceptRevision(@PathVariable Long projectId,
            @PathVariable Long versionId, @PathVariable @Min(0) int index, HttpServletRequest request) {
        return ApiResponse.success(service.acceptRevision(currentUser.currentUserId(), projectId, versionId, index), id(request));
    }
    private String id(HttpServletRequest request){return request.getHeader("X-Request-Id");}
    public record ApplyAnswersRequest(@NotNull Long ideaOriginVersionId){}
}
