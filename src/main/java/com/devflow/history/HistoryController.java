package com.devflow.history;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api")
public class HistoryController {

    public record RunDto(Long id, Long kitId, String kitName, String inputs, int fileCount, int minutesSaved,
                         Instant createdAt) {
    }

    public record Stats(long totalRuns, long runsLast7Days, long totalFiles, long totalMinutesSaved) {
    }

    private final GenerationRunRepository runs;

    public HistoryController(GenerationRunRepository runs) {
        this.runs = runs;
    }

    @GetMapping("/runs")
    @Transactional(readOnly = true)
    public List<RunDto> recent() {
        return runs.findTop25ByOrderByCreatedAtDesc().stream()
                .map(r -> new RunDto(r.getId(), r.getKitId(), r.getKitName(), r.getInputsSummary(),
                        r.getFileCount(), r.getMinutesSaved(), r.getCreatedAt()))
                .toList();
    }

    @GetMapping("/stats")
    @Transactional(readOnly = true)
    public Stats stats() {
        return new Stats(runs.count(),
                runs.countByCreatedAtAfter(Instant.now().minus(Duration.ofDays(7))),
                runs.totalFiles(),
                runs.totalMinutesSaved());
    }
}
