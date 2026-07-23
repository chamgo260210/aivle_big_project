package com.aivle.backend.controller;

import com.aivle.backend.dto.AnalysisResultDto;
import com.aivle.backend.service.DocumentAnalysisService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/project")
@CrossOrigin(origins = "*")
public class DocumentController {

    private final DocumentAnalysisService analysisService;

    public DocumentController(DocumentAnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @PostMapping("/upload-guideline")
    public ResponseEntity<?> uploadAndAnalyze(@RequestParam("file") MultipartFile file) {
        try {
            AnalysisResultDto result = analysisService.analyzeDocument(file);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            e.printStackTrace(); // IntelliJ 콘솔 창에 에러 원인 출력
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(e.getMessage());
        }
    }
}