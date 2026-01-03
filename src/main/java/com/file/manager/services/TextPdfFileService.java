package com.file.manager.services;

import com.file.manager.dto.FileDto;
import com.file.manager.dto.SummaryResponse;
import com.file.manager.enums.JobStatus;
import com.file.manager.models.Metadata;
import com.file.manager.repositories.MetadataRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
public class TextPdfFileService {

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Autowired
    private S3Client s3Client;
    @Autowired
    private MetadataRepository metadataRepository;
    @Autowired
    private BedrockService bedrockService;

    public JobStatus handleTextFile(FileDto file) {
        try {
            byte[] fileBytes = downloadFileFromS3(file.getS3Key());

            // Extract text with Tika
            String extractedText = extractTextFromFile(fileBytes);

            // Call Bedrock core
            SummaryResponse result = bedrockService.analyzeContent(extractedText, 40);

            Metadata metadata = Metadata.builder()
                    .id(UUID.randomUUID())
                    .fileId(file.getId())
                    .summary(result.getSummary())
                    .sensitiveFlag(result.isSensitive())
                    .confidentialFlag(result.isConfidential())
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            metadataRepository.save(metadata);

            return JobStatus.COMPLETED;
        } catch (Exception e) {
            log.error("Error while processing Fileid {} with type:{} Got error: {}",
                    file.getId(), file.getMimeType(), e.getMessage());
            return JobStatus.FAILED;
        }
    }

    private byte[] downloadFileFromS3(String key) throws UnsupportedEncodingException {
        String decodedKey = java.net.URLDecoder.decode(key, StandardCharsets.UTF_8);

        return s3Client.getObjectAsBytes(b -> b.bucket(bucketName).key(decodedKey))
                .asByteArray();
    }

    private String extractTextFromFile(byte[] fileBytes) throws Exception {
        try (InputStream stream = new ByteArrayInputStream(fileBytes)) {
            Tika tika = new Tika();
            return tika.parseToString(stream);
        }
    }
}
