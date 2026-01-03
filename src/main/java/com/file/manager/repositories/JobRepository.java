package com.file.manager.repositories;

import com.file.manager.models.Job;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobRepository extends JpaRepository<Job, Long> {
    // Additional custom methods if needed
}
