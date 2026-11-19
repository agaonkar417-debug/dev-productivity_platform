package com.devflow.kit;

import com.devflow.kit.KitDtos.FileDto;
import com.devflow.kit.KitDtos.KitRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * On startup, loads the built-in kits from src/main/resources/seed-kits/*.yml into the database.
 * Built-in kits are refreshed every start, so improving a YAML file updates the kit.
 */
@Component
public class KitSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(KitSeeder.class);

    private final KitService kitService;

    public KitSeeder(KitService kitService) {
        this.kitService = kitService;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        Resource[] resources = new PathMatchingResourcePatternResolver().getResources("classpath*:seed-kits/*.yml");
        for (Resource resource : resources) {
            try (InputStream in = resource.getInputStream()) {
                KitRequest kit = parse(new Yaml().load(in));
                kitService.upsertBuiltIn(kit);
                log.info("Loaded built-in kit '{}'", kit.slug());
            }
        }
    }

    @SuppressWarnings("unchecked")
    static KitRequest parse(Map<String, Object> yaml) {
        List<Map<String, Object>> files = (List<Map<String, Object>>) yaml.get("files");
        return new KitRequest(
                (String) yaml.get("slug"),
                (String) yaml.get("name"),
                (String) yaml.get("description"),
                (String) yaml.get("category"),
                ((Number) yaml.getOrDefault("minutesSaved", 0)).intValue(),
                files.stream().map(f -> new FileDto((String) f.get("path"), (String) f.get("content"))).toList());
    }
}
