package com.aivle.backend.service;

import com.aivle.backend.dto.AnalysisResultDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.*;

@Service
public class DocumentAnalysisService {

    @Value("${openai.api.key:}")
    private String openAiApiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public AnalysisResultDto analyzeDocument(MultipartFile file) throws Exception {
        if (openAiApiKey == null || openAiApiKey.isBlank()) {
            throw new IllegalStateException("OpenAI API Key가 설정되지 않았습니다. .env 또는 application.yaml을 확인해 주세요.");
        }

        // 1. Docx 텍스트 추출
        String fullText = extractTextFromDocx(file.getInputStream());
        if (fullText.trim().isEmpty()) {
            throw new IllegalArgumentException("업로드된 파일에서 텍스트를 추출할 수 없거나 내용이 비어있습니다.");
        }

        // 2. OpenAI API 호출
        return callOpenAiForAnalysis(fullText);
    }

    private String extractTextFromDocx(InputStream inputStream) throws Exception {
        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            StringBuilder sb = new StringBuilder();
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                sb.append(paragraph.getText()).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            System.err.println("Docx 파싱 실패: " + e.getMessage());
            throw new RuntimeException("Docx 파일 읽기 실패: 파일 양식을 확인해 주세요.", e);
        }
    }

    private AnalysisResultDto callOpenAiForAnalysis(String documentContent) throws Exception {
        RestTemplate restTemplate = new RestTemplate();
        String openAiUrl = "https://api.openai.com/v1/chat/completions";

        String systemPrompt = """
            당신은 사업기획서 작성 완성도를 심사하는 정밀 평가 에이전트입니다.
            제시된 사업기획서 텍스트에서 아래 12가지 필수 항목별 내용을 찾아서, 각 항목의 내용이 충분한지(PASS) 아니면 누락되거나 내용이 너무 부실하여 분석에 불충분한지(REJECT) 판단하세요.
            
            [필수 검토 12가지 항목]
            1. 사업 개요
            2. 시장 규모
            3. 타겟 고객
            4. 경쟁 분석
            5. 제품 · 서비스
            6. 비즈니스 모델
            7. 원가 · 수익성
            8. 판매 목표 · 재무 추정
            9. 기술 · 생산
            10. 법률 · 인허가
            11. 일정 · 리스크
            12. 근거 자료 목록
            
            [반환 JSON 형식을 반드시 엄수하세요]
            {
              "overallPassed": true/false (12개 모두 PASS이면 true, 하나라도 REJECT면 false),
              "itemResults": [
                {
                  "itemNumber": 1,
                  "itemName": "사업 개요",
                  "status": "PASS" 또는 "REJECT",
                  "reason": "REJECT인 경우 구체적 사유 작성 (PASS면 empty string)",
                  "extractedContent": "문서에서 추출된 관련 요약 내용"
                }
              ]
            }
            """;

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "gpt-4o-mini");
        requestBody.put("response_format", Map.of("type", "json_object"));

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "user", "content", "다음 사업기획서를 검증해 주세요:\n\n" + documentContent));

        requestBody.put("messages", messages);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(openAiApiKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(openAiUrl, entity, String.class);

            Map<String, Object> responseMap = objectMapper.readValue(response.getBody(), Map.class);
            List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            String contentJson = (String) message.get("content");

            return objectMapper.readValue(contentJson, AnalysisResultDto.class);
        } catch (HttpClientErrorException e) {
            System.err.println("OpenAI API 호출 실패 (상태 코드: " + e.getStatusCode() + "): " + e.getResponseBodyAsString());
            throw new RuntimeException("OpenAI API 연동 오류가 발생했습니다. 키 또는 잔액을 확인하세요.", e);
        }
    }
}