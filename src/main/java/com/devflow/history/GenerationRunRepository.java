package com.devflow.history;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface GenerationRunRepository extends JpaRepository<GenerationRun, Long> {

    List<GenerationRun> findTop25ByOrderByCreatedAtDesc();

    @Query("select coalesce(sum(r.minutesSaved), 0) from GenerationRun r")
    long totalMinutesSaved();

    @Query("select coalesce(sum(r.fileCount), 0) from GenerationRun r")
    long totalFiles();

    long countByCreatedAtAfter(Instant since);
}
