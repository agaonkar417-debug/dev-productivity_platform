package com.devflow.kit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Boots the whole application (with an in-memory database) and exercises the REST API. */
@SpringBootTest
@ActiveProfiles("test")
class DevFlowApiIntegrationTest {

    @Autowired
    WebApplicationContext context;

    @Autowired
    TaskKitRepository kits;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    private long builtInId(String slug) {
        return kits.findBySlug(slug).orElseThrow().getId();
    }

    private static final String REST_VARS =
            "{\"variables\":{\"basePackage\":\"com.acme.shop\",\"entity\":\"order item\",\"migrationVersion\":\"2\"}}";

    @Test
    void builtInKitsAreSeededOnStartup() throws Exception {
        mvc.perform(get("/api/kits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].slug", hasItem("spring-rest-feature")))
                .andExpect(jsonPath("$[*].slug", hasItem("bugfix-kit")))
                .andExpect(jsonPath("$[*].slug", hasItem("project-starter")))
                .andExpect(jsonPath("$[?(@.slug=='spring-rest-feature')].variables[*]",
                        org.hamcrest.Matchers.contains("basePackage", "entity", "migrationVersion")));
    }

    @Test
    void previewRendersPathsAndContentWithNamingStyles() throws Exception {
        mvc.perform(post("/api/kits/" + builtInId("spring-rest-feature") + "/preview")
                        .contentType(MediaType.APPLICATION_JSON).content(REST_VARS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files[*].path",
                        hasItem("src/main/java/com/acme/shop/orderitem/OrderItemController.java")))
                .andExpect(jsonPath("$.files[*].path",
                        hasItem("src/main/resources/db/migration/V2__create_order_items.sql")))
                .andExpect(jsonPath("$.files[4].path").value(containsString("OrderItemController.java")))
                .andExpect(jsonPath("$.files[4].content",
                        containsString("@RequestMapping(\"/api/order-items\")")));
    }

    @Test
    void generateReturnsZipAndRecordsHistory() throws Exception {
        long before = statsTotalRuns();

        MvcResult result = mvc.perform(post("/api/kits/" + builtInId("spring-rest-feature") + "/generate")
                        .contentType(MediaType.APPLICATION_JSON).content(REST_VARS))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/zip"))
                .andExpect(header().string("Content-Disposition", containsString(".zip")))
                .andReturn();

        Map<String, String> entries = unzip(result.getResponse().getContentAsByteArray());
        assertThat(entries).containsKey("src/main/java/com/acme/shop/orderitem/OrderItem.java");
        assertThat(entries.get("src/main/java/com/acme/shop/orderitem/OrderItem.java"))
                .contains("package com.acme.shop.orderitem;")
                .contains("@Table(name = \"order_items\")");

        assertThat(statsTotalRuns()).isEqualTo(before + 1);
        mvc.perform(get("/api/runs"))
                .andExpect(jsonPath("$[0].kitName").value("Spring Boot REST feature"))
                .andExpect(jsonPath("$[0].inputs", containsString("entity=order item")));
        mvc.perform(get("/api/stats")).andExpect(jsonPath("$.totalMinutesSaved", greaterThanOrEqualTo(60)));
    }

    @Test
    void missingVariablesGiveAHelpful400() throws Exception {
        mvc.perform(post("/api/kits/" + builtInId("bugfix-kit") + "/generate")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"variables\":{\"ticket\":\"BUG-1\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.problems", hasItem("summary is required")))
                .andExpect(jsonPath("$.problems", hasItem("component is required")));
    }

    @Test
    void customKitLifecycle() throws Exception {
        String body = """
                {"name":"Release notes","category":"Docs","minutesSaved":10,
                 "files":[{"path":"releases/{{version}}.md","content":"# {{project|title}} {{version}}\\n"}]}
                """;
        MvcResult created = mvc.perform(post("/api/kits").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("release-notes"))
                .andExpect(jsonPath("$.variables[0]").value("version"))
                .andExpect(jsonPath("$.variables[1]").value("project"))
                .andReturn();
        long id = idOf(created);

        mvc.perform(post("/api/kits/" + id + "/preview").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"variables\":{\"version\":\"1.4.0\",\"project\":\"devflow ai\"}}"))
                .andExpect(jsonPath("$.files[0].path").value("releases/1.4.0.md"))
                .andExpect(jsonPath("$.files[0].content").value("# Devflow Ai 1.4.0\n"));

        mvc.perform(put("/api/kits/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("Release notes", "Release notes v2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Release notes v2"))
                .andExpect(jsonPath("$.slug").value("release-notes"));

        mvc.perform(get("/api/kits/" + id + "/export"))
                .andExpect(header().string("Content-Disposition", containsString("release-notes.devflow-kit.json")))
                .andExpect(jsonPath("$.files[0].path").value("releases/{{version}}.md"));

        // importing the same kit again gets a unique slug
        mvc.perform(post("/api/kits").contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("{\"name\"", "{\"slug\":\"release-notes\",\"name\"")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("release-notes-2"));

        MvcResult dup = mvc.perform(post("/api/kits/" + id + "/duplicate"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Release notes v2 (copy)"))
                .andReturn();

        mvc.perform(delete("/api/kits/" + id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/kits/" + id)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/kits/" + idOf(dup))).andExpect(status().isNoContent());
    }

    @Test
    void builtInKitsAreReadOnly() throws Exception {
        long id = builtInId("bugfix-kit");
        mvc.perform(delete("/api/kits/" + id)).andExpect(status().isConflict());
        mvc.perform(put("/api/kits/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"minutesSaved\":1,\"files\":[{\"path\":\"a\",\"content\":\"\"}]}"))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidKitsAreRejected() throws Exception {
        mvc.perform(post("/api/kits").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"minutesSaved\":1,\"files\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.problems").isArray());

        mvc.perform(post("/api/kits").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bad\",\"minutesSaved\":1,\"files\":[{\"path\":\"{{x|shout}}\",\"content\":\"\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.problems[0]", containsString("Unknown filter 'shout'")));
    }

    @Test
    void pathTraversalThroughVariablesIsBlocked() throws Exception {
        MvcResult created = mvc.perform(post("/api/kits").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Traversal\",\"minutesSaved\":1,\"files\":[{\"path\":\"out/{{name}}.txt\",\"content\":\"x\"}]}"))
                .andExpect(status().isCreated()).andReturn();
        long id = idOf(created);

        mvc.perform(post("/api/kits/" + id + "/generate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"variables\":{\"name\":\"../../.bashrc\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("A generated file path is not safe"));
        mvc.perform(delete("/api/kits/" + id)).andExpect(status().isNoContent());
    }

    @Test
    void healthEndpointReportsDatabase() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.db.status").value("UP"));
    }

    @Test
    void frontendIsServed() throws Exception {
        mvc.perform(get("/index.html")).andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(containsString("DevFlow")));
    }

    // -------------------------------------------------------------------------------------

    private long statsTotalRuns() throws Exception {
        String json = mvc.perform(get("/api/stats")).andReturn().getResponse().getContentAsString();
        return Long.parseLong(json.replaceAll(".*\"totalRuns\":(\\d+).*", "$1"));
    }

    private static long idOf(MvcResult result) throws Exception {
        String json = result.getResponse().getContentAsString();
        return Long.parseLong(json.replaceAll("^\\{\"id\":(\\d+).*$", "$1"));
    }

    private static Map<String, String> unzip(byte[] bytes) throws Exception {
        Map<String, String> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                entries.put(e.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return entries;
    }
}
