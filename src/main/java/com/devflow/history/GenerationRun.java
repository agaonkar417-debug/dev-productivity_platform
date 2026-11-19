package com.devflow.history;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** A record of one "Download ZIP": what was generated and roughly how much time it saved. */
@Entity
@Table(name = "generation_run")
public class GenerationRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "kit_id")
    private Long kitId;

    @Column(name = "kit_name", nullable = false)
    private String kitName;

    @Column(name = "inputs_summary", nullable = false)
    private String inputsSummary;

    @Column(name = "file_count", nullable = false)
    private int fileCount;

    @Column(name = "minutes_saved", nullable = false)
    private int minutesSaved;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected GenerationRun() {
    }

    public GenerationRun(Long kitId, String kitName, String inputsSummary, int fileCount, int minutesSaved) {
        this.kitId = kitId;
        this.kitName = kitName;
        this.inputsSummary = inputsSummary;
        this.fileCount = fileCount;
        this.minutesSaved = minutesSaved;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getKitId() { return kitId; }
    public String getKitName() { return kitName; }
    public String getInputsSummary() { return inputsSummary; }
    public int getFileCount() { return fileCount; }
    public int getMinutesSaved() { return minutesSaved; }
    public Instant getCreatedAt() { return createdAt; }
}
