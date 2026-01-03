package com.file.manager.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "metadata", schema = "filesystem")
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class Metadata {
    @Id
    @Column(nullable = false)
    private UUID id;


    @JoinColumn( nullable = false)
    private UUID fileId;

    @ElementCollection
    @CollectionTable(name = "metadata_ai_tag", schema = "filesystem", joinColumns = @JoinColumn(name = "metadata_id"))
    @Column(name = "ai_tag")
    private java.util.List<String> aiTag;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(nullable = false)
    private Boolean sensitiveFlag = false;

    @Column(nullable = false)
    private Boolean confidentialFlag = false;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
}