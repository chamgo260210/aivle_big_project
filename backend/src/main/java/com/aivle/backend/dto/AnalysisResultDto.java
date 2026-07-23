package com.aivle.backend.dto;

import lombok.*;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisResultDto {

    private boolean overallPassed;
    private List<ItemResult> itemResults;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ItemResult {
        private int itemNumber;
        private String itemName;
        private String status;          // PASS 또는 REJECT
        private String reason;          // REJECT 사유
        private String extractedContent; // 추출된 내용
    }
}