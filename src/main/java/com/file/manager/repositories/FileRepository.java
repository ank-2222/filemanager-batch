package com.file.manager.repositories;

import com.file.manager.models.File;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FileRepository extends JpaRepository<File, UUID> {
    // Additional custom methods if needed]

    Boolean existsByNameAndFolderId(String name, UUID folderId);
}