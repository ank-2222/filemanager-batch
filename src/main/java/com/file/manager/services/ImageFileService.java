package com.file.manager.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.file.manager.dto.FileDto;
import com.file.manager.enums.JobStatus;
import com.file.manager.models.Metadata;
import com.file.manager.repositories.MetadataRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class ImageFileService {

    @Value("${aws.s3.bucket-name}")
    private String bucketName;
    @Value("${aws.bedrock.model-id}")
    private String bedrockModelId;   // configurable

    private final int wordLimit = 20;
    @Autowired
    private RekognitionClient rekognitionClient;
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MetadataRepository metadataRepository;
    @Autowired
    private BedrockRuntimeClient bedrockRuntimeClient;

    public JobStatus handleImageFile(FileDto file) {
        try {
            String key = file.getS3Key();
            S3Object s3Object = S3Object.builder()
                    .bucket(bucketName)
                    .name(key)
                    .build();

            Image myImage = Image.builder()
                    .s3Object(s3Object)
                    .build();

            // Detect labels
            List<String> aiTags = detectLabels(myImage);

            // Detect unsafe content
            boolean sensitive = detectModeration(myImage);

            // Detect text + check if confidential
            String extractedText = detectText(myImage);
            String extractSummary = null;
            boolean confidential = false;

            if (!extractedText.isEmpty()) {
                confidential = checkConfidential(extractedText);

                // Summarize using Bedrock
                extractSummary = getSummarization(extractedText, wordLimit);
            }else{

            extractSummary = getSummarization(aiTags.toString(),wordLimit);
            }
            Metadata metadata = Metadata.builder()
                    .id(UUID.randomUUID())
                    .fileId(file.getId())
                    .aiTag(aiTags)
                    .summary(extractSummary)
                    .sensitiveFlag(sensitive)
                    .confidentialFlag(confidential)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            metadataRepository.save(metadata);
            return JobStatus.COMPLETED;

        } catch (Exception e) {
            log.error("Error while processing Fileid {} with type:{} Got error: {}",file.getId(),file.getMimeType(),e.getMessage());
            return JobStatus.FAILED;
        }
    }

    // 🔹 Detect Labels
    private List<String> detectLabels(Image myImage) {
        DetectLabelsRequest request = DetectLabelsRequest.builder()
                .image(myImage)
                .maxLabels(10)
                .build();

        DetectLabelsResponse response = rekognitionClient.detectLabels(request);

        List<Label> labels = response.labels();



        return labels.stream()
                .filter(label -> label.confidence() >= 95.0f)
                .map(Label::name)
                .toList();
    }

    // 🔹 Detect Unsafe Content
    private boolean detectModeration(Image myImage) {
        DetectModerationLabelsRequest request = DetectModerationLabelsRequest.builder()
                .image(myImage)
                .minConfidence(90F)
                .build();

        DetectModerationLabelsResponse response = rekognitionClient.detectModerationLabels(request);

        List<ModerationLabel> moderationLabels = response.moderationLabels();


        return moderationLabels.stream()
                .anyMatch(label -> label.confidence() >= 90.0f);
    }

    // 🔹 Detect Text (OCR)
    private String detectText(Image myImage) {
        DetectTextRequest request = DetectTextRequest.builder()
                .image(myImage)
                .build();

        DetectTextResponse response = rekognitionClient.detectText(request);

        StringBuilder extracted = new StringBuilder();

        for (TextDetection text : response.textDetections()) {
            if ("LINE".equals(text.typeAsString())) {
                extracted.append(text.detectedText()).append(" ");
            }
        }

        return extracted.toString().trim();
    }

    // 🔹 Check if text looks confidential
    private boolean checkConfidential(String text) {
        if (text == null || text.isBlank()) return false;

        String lower = text.toLowerCase();

        // 🔹 Keyword checks
        if (lower.contains("invoice") ||
                lower.contains("confidential") ||
                lower.contains("passport") ||
                lower.contains("driver license") ||
                lower.contains("ssn") ||
                lower.contains("id") ||
                lower.contains("credit") ||
                lower.contains("debit") ||
                lower.contains("bank") ||
                lower.contains("form") ||
                lower.contains("aadhaar")) {
            return true;
        }

        // 🔹 Regex checks
        // Credit card (16 digits with optional spaces/dashes)
        if (text.matches(".*(?:\\d[ -]?){13,16}.*")) return true;

        // API key-like tokens (long alphanumeric strings)
        if (text.matches(".*[A-Za-z0-9_-]{20,}.*")) return true;

        // Date of birth / sensitive dates (dd/mm/yyyy or mm/dd/yyyy)
        if (text.matches(".*\\b\\d{2}[/-]\\d{2}[/-]\\d{2,4}\\b.*")) return true;

        return false;
    }

    private String getSummarization(String inputText, int wordLimit) {
        try {
            String prompt = String.format(
                    "Write a concise description in one single line of maximum %d words. " +
                    "Do not use words like 'image', 'picture', 'photo', 'depicts', or pronouns such as 'it', 'this', 'that'. " +
                    "Only provide a neutral, direct description of the content:\n\n%s",
                    wordLimit, inputText
            );


            Map<String, Object> body = Map.of(
                    "inputText", prompt,
                    "textGenerationConfig", Map.of(
                            "maxTokenCount", 200,
                            "temperature", 0.7,
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

            String responseBody = response.body().asUtf8String();

            Map<String, Object> json = objectMapper.readValue(responseBody, Map.class);
            String outputText = ((List<Map<String, String>>) json.get("results"))
                    .get(0).get("outputText");

            return outputText.trim();

        } catch (Exception e) {
            log.error("Summarization failed, fallback to partial OCR. Error: {}", e.getMessage());
            // fallback: return first 20 words of OCR
            return inputText.split("\\s+").length > 20 ?
                    String.join(" ", List.of(inputText.split("\\s+")).subList(0, 20)) + "..." :
                    inputText;
        }
    }

    }
