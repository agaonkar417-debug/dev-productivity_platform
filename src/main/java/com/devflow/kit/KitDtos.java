package com.devflow.kit;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/** The JSON shapes the API sends and receives. Entities never leave the service layer. */
public final class KitDtos {

    private KitDtos() {
    }

    public record FileDto(
            @NotBlank @Size(max = 500) String path,
            @Size(max = 200_000) String content) {
    }

    /** Body for creating/updating a kit. The same shape is used for export/import. */
    public record KitRequest(
            @Size(max = 80) @Pattern(regexp = "^$|^[a-z0-9]+(-[a-z0-9]+)*$",
                    message = "use lower-case letters, digits and dashes") String slug,
            @NotBlank @Size(max = 120) String name,
            @Size(max = 2000) String description,
            @Size(max = 60) String category,
            @Min(0) @Max(1000) int minutesSaved,
            @NotEmpty @Valid List<FileDto> files) {
    }

    public record KitSummary(
            Long id, String slug, String name, String description, String category,
            int minutesSaved, boolean builtIn, int fileCount, List<String> variables, Instant updatedAt) {
    }

    public record KitDetail(
            Long id, String slug, String name, String description, String category,
            int minutesSaved, boolean builtIn, List<String> variables, List<FileDto> files, Instant updatedAt) {
    }
}
