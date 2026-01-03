package com.file.manager.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "file", schema = "filesystem")
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class File {

    @Id
    @NotNull(message = "File id cannot be null")
    @Column(nullable = false)
    private UUID id;

    @NotNull(message = "File name cannot be blank")
    @Column(nullable = false)
    private String name;

    @NotNull(message = "File URL cannot be blank")
    @Column(nullable = false)
    private String fileUrl;

    @NotNull(message = "MIME type cannot be blank")
    @Column(nullable = false)
    private String mimeType;

    @NotNull(message = "File size cannot be null")
    @Min(value = 1, message = "File size must be at least 1 byte")
    @Column(nullable = false)
    private Long fileSize;

    @NotNull(message = "Owner ID cannot be null")
    @Column(nullable = false)
    private UUID ownerId; // FK to accounts.user.id

    @Column(nullable = false)
    private UUID folderId;

    @NotNull(message = "Folder path cannot be blank")
    @Column(nullable = false)
    private String folderPath;

    @Column(nullable = false)
    private String s3Key;

    @NotNull(message = "Creation date cannot be null")
    @Column(nullable = false)
    private LocalDateTime createdAt;

    @NotNull(message = "Update date cannot be null")
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
