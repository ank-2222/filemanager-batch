package com.file.manager.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.file.manager.dto.SummaryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BedrockService {

    private final BedrockRuntimeClient bedrockRuntimeClient;
    private final ObjectMapper objectMapper;

    @Value("${aws.bedrock.model-id}")
    private String bedrockModelId;   // configurable via properties

    public SummaryResponse analyzeContent(String content, int wordLimit) {
        try {
            // Ask Titan with specialized prompts
            String summary = askTitan(buildSummaryPrompt(content, wordLimit));
            String tagsCsv = askTitan(buildTagsPrompt(content));
            String sensitiveStr = askTitan(buildSensitivePrompt(content));
            String confidentialStr = askTitan(buildConfidentialPrompt(content));

            // Process tags: split, dedup, limit 8
            List<String> tags = Arrays.stream(tagsCsv.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(this::normalizeTag)
                    .distinct()
                    .limit(8)
                    .collect(Collectors.toList());

            boolean sensitive = parseBoolean(sensitiveStr);
            boolean confidential = parseBoolean(confidentialStr);

          return SummaryResponse.builder()
                    .tags(tags)
                    .summary(summary)
                    .isConfidential(confidential)
                    .isSensitive(sensitive)
                    .build();


        } catch (Exception e) {
            log.error("Bedrock analysis failed: {}", e.getMessage());
            SummaryResponse fallback = new SummaryResponse();
            fallback.setSummary(null);
            fallback.setTags(List.of());
            fallback.setSensitive(false);
            fallback.setConfidential(false);
            return fallback;
        }
    }

    private String askTitan(String prompt) throws Exception {
        Map<String, Object> body = Map.of(
                "inputText", prompt,
                "textGenerationConfig", Map.of(
                        "maxTokenCount", 200,
                        "temperature", 0.0,
                        "topP", 0.9
                )
        );
        String requestBody = objectMapper.writeValueAsString(body);

        InvokeModelRequest request = InvokeModelRequest.builder()
                .modelId(bedrockModelId)
                .accept("application/json")
                .contentType("application/json")
                .body(SdkBytes.fromString(requestBody, StandardCharsets.UTF_8))
                .build();

        InvokeModelResponse response = bedrockRuntimeClient.invokeModel(request);
        Map<String, Object> json = objectMapper.readValue(response.body().asUtf8String(), Map.class);
        String outputText = ((List<Map<String, String>>) json.get("results"))
                .get(0).get("outputText");

        return sanitizeOutput(outputText);
    }

    // -------- Prompt Builders --------

    private String buildSummaryPrompt(String content, int wordLimit) {
        return String.format(
                """
                The following is text from a document:
                %s
        
                Summarize the document in one paragraph, using no more than %d words. 
                Respond ONLY with the paragraph; do not include headings, labels, or extra text.
                """,
                content, wordLimit
        );
    }


    private String buildTagsPrompt(String content) {
        return String.format("""
        %s

       please provide comma separated 5 tags which describes above content.\s""", content);
    }


    private String buildSensitivePrompt(String content) {
        return String.format("""
        %s

        Does the text contain sensitive information (PII, financial, health, or personal data)? Choose from the following:
        true
        false
        """, content);
    }

    private String buildConfidentialPrompt(String content) {
        return String.format("""
        %s

        Does the text contain confidential business information (contracts, invoices, corporate secrets, or internal documents)? Choose from the following:
        true
        false
        """, content);
    }


    // -------- Helpers --------

    private String sanitizeOutput(String output) {
        if (output == null) return "";
        return output.replaceAll("(?s)```.*?```", "")
                .replace("`", "")
                .trim();
    }

    private boolean parseBoolean(String value) {
        return value != null && value.trim().equalsIgnoreCase("true");
    }

    private String normalizeTag(String tag) {
        // Normalize casing, remove trailing punctuation
        return tag.replaceAll("[^a-zA-Z0-9\\s]", "").trim();
    }
}
