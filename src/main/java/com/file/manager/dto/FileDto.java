package com.file.manager.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileDto {

    private UUID id;
    private String name;
    private String fileUrl;
    private String mimeType;
    private Long fileSize;
    private UUID ownerId;
    private UUID folderId;
    private String folderPath;
    private String s3Key;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
