package com.devflow.generate;

import com.devflow.DevFlowLimits;
import com.devflow.history.GenerationRun;
import com.devflow.history.GenerationRunRepository;
import com.devflow.kit.KitFile;
import com.devflow.kit.KitService;
import com.devflow.kit.TaskKit;
import com.devflow.web.ApiErrors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Fills a kit's templates with the user's values and packages the result. */
@Service
public class GenerationService {

    public record GeneratedFile(String path, String content) {
    }

    public record Result(String kitSlug, List<GeneratedFile> files) {
    }

    private final KitService kitService;
    private final GenerationRunRepository runs;
    private final DevFlowLimits limits;

    public GenerationService(KitService kitService, GenerationRunRepository runs, DevFlowLimits limits) {
        this.kitService = kitService;
        this.runs = runs;
        this.limits = limits;
    }

    /** Renders the files without recording anything (used for the live preview). */
    @Transactional(readOnly = true)
    public Result preview(long kitId, Map<String, String> values) {
        TaskKit kit = kitService.find(kitId);
        return new Result(kit.getSlug(), render(kit, values));
    }

    /** Renders the files, records a history entry and returns a ZIP archive. */
    @Transactional
    public byte[] generateZip(long kitId, Map<String, String> values) {
        TaskKit kit = kitService.find(kitId);
        List<GeneratedFile> files = render(kit, values);
        runs.save(new GenerationRun(kit.getId(), kit.getName(), summarize(kit, values), files.size(),
                kit.getMinutesSaved()));
        return zip(files);
    }

    List<GeneratedFile> render(TaskKit kit, Map<String, String> rawValues) {
        Map<String, String> values = new LinkedHashMap<>();
        List<String> problems = new ArrayList<>();
        Map<String, String> input = rawValues == null ? Map.of() : rawValues;
        for (String name : KitService.variables(kit)) {
            String v = input.get(name);
            if (v == null || v.isBlank()) {
                problems.add(name + " is required");
            } else if (v.length() > limits.maxVariableLength()) {
                problems.add(name + " must be at most " + limits.maxVariableLength() + " characters");
            } else if (v.contains("\n") || v.contains("\r")) {
                problems.add(name + " must be a single line");
            } else {
                values.put(name, v.strip());
            }
        }
        if (!problems.isEmpty()) {
            throw new ApiErrors.BadInput("Please fill in the missing values", problems);
        }

        List<GeneratedFile> out = new ArrayList<>();
        Set<String> paths = new HashSet<>();
        for (KitFile f : kit.getFiles()) {
            String path = safePath(TemplateEngine.render(f.getPathTemplate(), values));
            if (!paths.add(path)) {
                throw new ApiErrors.BadInput("Two files would be written to the same path: " + path);
            }
            out.add(new GeneratedFile(path, TemplateEngine.render(f.getContent(), values)));
        }
        return out;
    }

    /**
     * Generated paths must stay inside the folder the user extracts the ZIP into.
     * Rejecting "..", absolute paths and drive letters prevents "zip slip" attacks,
     * where a crafted kit could overwrite files elsewhere on the developer's machine.
     */
    static String safePath(String rendered) {
        String p = rendered.strip().replace('\\', '/');
        while (p.startsWith("./")) {
            p = p.substring(2);
        }
        List<String> problems = new ArrayList<>();
        if (p.isEmpty()) {
            problems.add("A file path is empty");
        } else if (p.startsWith("/") || p.matches("^[A-Za-z]:.*")) {
            problems.add("Absolute paths are not allowed: " + rendered);
        } else {
            for (String segment : p.split("/", -1)) {
                if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                    problems.add("Invalid path (empty, '.' or '..' segment): " + rendered);
                    break;
                }
                if (segment.chars().anyMatch(c -> c < 32 || "<>:\"|?*".indexOf(c) >= 0)) {
                    problems.add("Path contains characters that are not allowed in file names: " + rendered);
                    break;
                }
            }
        }
        if (!problems.isEmpty()) {
            throw new ApiErrors.BadInput("A generated file path is not safe", problems);
        }
        return p;
    }

    static byte[] zip(List<GeneratedFile> files) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            for (GeneratedFile f : files) {
                zip.putNextEntry(new ZipEntry(f.path()));
                zip.write(f.content().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    private static String summarize(TaskKit kit, Map<String, String> values) {
        String s = KitService.variables(kit).stream()
                .map(name -> name + "=" + values.get(name).strip())
                .collect(Collectors.joining(", "));
        return s.length() > 1000 ? s.substring(0, 997) + "..." : s;
    }
}
