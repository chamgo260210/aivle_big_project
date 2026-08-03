package com.aivle.backend.journey;

import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.common.security.CurrentUserProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v2/projects/{projectId}")
@RequiredArgsConstructor
public class JourneyController {
    private final JourneyAiService journey;
    private final CurrentUserProvider currentUser;

    @PostMapping(value = "/ideas", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<JourneyAiService.IdeaSourceView> saveText(@PathVariable Long projectId,
            @Valid @RequestBody TextIdeaRequest body, HttpServletRequest request) {
        return ApiResponse.success(journey.saveText(currentUser.currentUserId(), projectId, body.title(), body.text()), id(request));
    }

    @PostMapping(value = "/ideas", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<JourneyAiService.IdeaSourceView> saveFile(@PathVariable Long projectId,
            @RequestParam(required = false) String title, @RequestPart MultipartFile file, HttpServletRequest request) {
        return ApiResponse.success(journey.saveFile(currentUser.currentUserId(), projectId, title, file), id(request));
    }

    @GetMapping("/ideas/current")
    public ApiResponse<JourneyAiService.IdeaSourceView> currentIdea(@PathVariable Long projectId, HttpServletRequest request) {
        return ApiResponse.success(journey.currentIdea(currentUser.currentUserId(), projectId), id(request));
    }

    @PostMapping("/idea-interpretations")
    public ApiResponse<JourneyAiService.InterpretationView> interpret(@PathVariable Long projectId, HttpServletRequest request) {
        return ApiResponse.success(journey.interpret(currentUser.currentUserId(), projectId), id(request));
    }

    @GetMapping("/idea-interpretations/current")
    public ApiResponse<JourneyAiService.InterpretationView> currentInterpretation(@PathVariable Long projectId, HttpServletRequest request) {
        return ApiResponse.success(journey.currentInterpretation(currentUser.currentUserId(), projectId), id(request));
    }

    @PostMapping("/idea-versions/{ideaVersionId}/confirm")
    public ApiResponse<JourneyAiService.IdeaVersionView> confirm(@PathVariable Long projectId,
            @PathVariable Long ideaVersionId, HttpServletRequest request) {
        return ApiResponse.success(journey.confirm(currentUser.currentUserId(), projectId, ideaVersionId), id(request));
    }

    @PostMapping("/legal-reviews")
    public ApiResponse<JourneyAiService.LegalView> legal(@PathVariable Long projectId, HttpServletRequest request) {
        return ApiResponse.success(journey.legalReview(currentUser.currentUserId(), projectId), id(request));
    }

    @GetMapping("/legal-reviews/current")
    public ApiResponse<JourneyAiService.LegalView> currentLegal(@PathVariable Long projectId, HttpServletRequest request) {
        return ApiResponse.success(journey.currentLegal(currentUser.currentUserId(), projectId), id(request));
    }

    private String id(HttpServletRequest request) { return request.getHeader("X-Request-Id"); }
    public record TextIdeaRequest(@Size(max = 200) String title, @NotBlank @Size(max = 200000) String text) { }
}
