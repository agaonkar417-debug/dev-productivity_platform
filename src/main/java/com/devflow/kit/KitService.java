package com.devflow.kit;

import com.devflow.DevFlowLimits;
import com.devflow.generate.CaseConverter;
import com.devflow.generate.TemplateEngine;
import com.devflow.kit.KitDtos.FileDto;
import com.devflow.kit.KitDtos.KitDetail;
import com.devflow.kit.KitDtos.KitRequest;
import com.devflow.kit.KitDtos.KitSummary;
import com.devflow.web.ApiErrors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

@Service
public class KitService {

    private final TaskKitRepository kits;
    private final DevFlowLimits limits;

    public KitService(TaskKitRepository kits, DevFlowLimits limits) {
        this.kits = kits;
        this.limits = limits;
    }

    @Transactional(readOnly = true)
    public List<KitSummary> list() {
        return kits.findAllByOrderByBuiltInDescNameAsc().stream().map(KitService::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public KitDetail get(long id) {
        return toDetail(find(id));
    }

    /** Loads the entity for other services (e.g. generation). Call inside a transaction. */
    public TaskKit find(long id) {
        return kits.findById(id).orElseThrow(() -> new ApiErrors.NotFound("Kit " + id + " not found"));
    }

    @Transactional
    public KitDetail create(KitRequest req) {
        validate(req);
        TaskKit kit = new TaskKit();
        kit.setSlug(uniqueSlug(req.slug() == null || req.slug().isBlank() ? req.name() : req.slug()));
        apply(kit, req);
        return toDetail(kits.save(kit));
    }

    @Transactional
    public KitDetail update(long id, KitRequest req) {
        TaskKit kit = find(id);
        requireEditable(kit);
        validate(req);
        apply(kit, req);
        return toDetail(kits.saveAndFlush(kit));
    }

    @Transactional
    public void delete(long id) {
        TaskKit kit = find(id);
        requireEditable(kit);
        kits.delete(kit);
    }

    @Transactional
    public KitDetail duplicate(long id) {
        KitRequest copy = export(id);
        return create(new KitRequest(null, copy.name() + " (copy)", copy.description(), copy.category(),
                copy.minutesSaved(), copy.files()));
    }

    @Transactional(readOnly = true)
    public KitRequest export(long id) {
        TaskKit kit = find(id);
        return new KitRequest(kit.getSlug(), kit.getName(), kit.getDescription(), kit.getCategory(),
                kit.getMinutesSaved(), files(kit));
    }

    /** Creates or refreshes a built-in kit shipped with the app (see KitSeeder). */
    @Transactional
    public void upsertBuiltIn(KitRequest req) {
        validate(req);
        TaskKit kit = kits.findBySlug(req.slug()).orElseGet(TaskKit::new);
        kit.setSlug(req.slug());
        kit.setBuiltIn(true);
        apply(kit, req);
        kits.save(kit);
    }

    // ---------------------------------------------------------------------------------------

    private void apply(TaskKit kit, KitRequest req) {
        kit.setName(req.name().strip());
        kit.setDescription(blankToNull(req.description()));
        kit.setCategory(blankToNull(req.category()));
        kit.setMinutesSaved(req.minutesSaved());
        kit.replaceFiles(req.files().stream()
                .map(f -> new KitFile(f.path().strip(), f.content() == null ? "" : f.content()))
                .toList());
    }

    private void validate(KitRequest req) {
        List<String> problems = new ArrayList<>();
        if (req.files().size() > limits.maxFilesPerKit()) {
            problems.add("A kit can have at most " + limits.maxFilesPerKit() + " files");
        }
        Set<String> seen = new HashSet<>();
        for (FileDto f : req.files()) {
            String path = f.path().strip();
            if (!seen.add(path)) {
                problems.add("Duplicate file path: " + path);
            }
            if (f.content() != null && f.content().length() > limits.maxFileChars()) {
                problems.add(path + ": file is longer than " + limits.maxFileChars() + " characters");
            }
            problems.addAll(TemplateEngine.validate(path).stream().map(p -> path + ": " + p).toList());
            problems.addAll(TemplateEngine.validate(f.content()).stream().map(p -> path + ": " + p).toList());
        }
        if (!problems.isEmpty()) {
            throw new ApiErrors.BadInput("The kit has problems", problems);
        }
    }

    private void requireEditable(TaskKit kit) {
        if (kit.isBuiltIn()) {
            throw new ApiErrors.Conflict("Built-in kits are read-only. Duplicate the kit to customize it.");
        }
    }

    private String uniqueSlug(String source) {
        String base = CaseConverter.kebab(source);
        if (base.isEmpty()) {
            base = "kit";
        }
        base = base.length() > 70 ? base.substring(0, 70) : base;
        String slug = base;
        for (int i = 2; kits.existsBySlug(slug); i++) {
            slug = base + "-" + i;
        }
        return slug;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    static List<FileDto> files(TaskKit kit) {
        return kit.getFiles().stream().map(f -> new FileDto(f.getPathTemplate(), f.getContent())).toList();
    }

    public static List<String> variables(TaskKit kit) {
        return List.copyOf(TemplateEngine.findVariables(kit.getFiles().stream()
                .flatMap(f -> Stream.of(f.getPathTemplate(), f.getContent()))
                .toList()));
    }

    private static KitSummary toSummary(TaskKit k) {
        return new KitSummary(k.getId(), k.getSlug(), k.getName(), k.getDescription(), k.getCategory(),
                k.getMinutesSaved(), k.isBuiltIn(), k.getFiles().size(), variables(k), k.getUpdatedAt());
    }

    private static KitDetail toDetail(TaskKit k) {
        return new KitDetail(k.getId(), k.getSlug(), k.getName(), k.getDescription(), k.getCategory(),
                k.getMinutesSaved(), k.isBuiltIn(), variables(k), files(k), k.getUpdatedAt());
    }
}
