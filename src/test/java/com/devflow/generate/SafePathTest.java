package com.devflow.generate;

import com.devflow.web.ApiErrors;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SafePathTest {

    @ParameterizedTest
    @CsvSource({
            "src/Main.java, src/Main.java",
            "./docs/a.md, docs/a.md",
            "'  .github/workflows/ci.yml ', .github/workflows/ci.yml",
            "dir\\file.txt, dir/file.txt",
    })
    void acceptsRelativePaths(String input, String expected) {
        assertThat(GenerationService.safePath(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/etc/passwd", "../outside.txt", "a/../../b", "C:/Windows/x", "a//b", "a/./b", "a/b?.txt"})
    void rejectsPathsThatCouldEscapeTheTargetFolder(String input) {
        assertThatThrownBy(() -> GenerationService.safePath(input)).isInstanceOf(ApiErrors.BadInput.class);
    }
}
