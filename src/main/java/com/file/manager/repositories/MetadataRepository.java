package com.file.manager.repositories;

import com.file.manager.models.Metadata;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MetadataRepository extends JpaRepository<Metadata, Long> {
    // Additional custom methods if needed
}
