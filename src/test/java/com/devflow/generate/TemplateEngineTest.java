package com.devflow.generate;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemplateEngineTest {

    @Test
    void caseConversionsHandleSpacesCamelCaseAndSeparators() {
        for (String input : List.of("order item", "OrderItem", "orderItem", "order_item", "order-item", "ORDER ITEM")) {
            assertThat(CaseConverter.pascal(input)).as(input).isEqualTo("OrderItem");
            assertThat(CaseConverter.camel(input)).as(input).isEqualTo("orderItem");
            assertThat(CaseConverter.kebab(input)).as(input).isEqualTo("order-item");
            assertThat(CaseConverter.snake(input)).as(input).isEqualTo("order_item");
            assertThat(CaseConverter.constant(input)).as(input).isEqualTo("ORDER_ITEM");
            assertThat(CaseConverter.title(input)).as(input).isEqualTo("Order Item");
        }
        assertThat(CaseConverter.kebab("HTTPServer v2")).isEqualTo("http-server-v2");
    }

    @Test
    void pluralCoversCommonEnglishEndings() {
        assertThat(CaseConverter.plural("order item")).isEqualTo("order items");
        assertThat(CaseConverter.plural("category")).isEqualTo("categories");
        assertThat(CaseConverter.plural("day")).isEqualTo("days");
        assertThat(CaseConverter.plural("box")).isEqualTo("boxes");
        assertThat(CaseConverter.plural("branch")).isEqualTo("branches");
        assertThat(CaseConverter.plural("BOX")).isEqualTo("BOXES");
    }

    @Test
    void rendersFiltersLeftToRight() {
        String out = TemplateEngine.render(
                "class {{entity|pascal}} -> table {{ entity | plural | snake }} in {{pkg|path}}",
                Map.of("entity", "order item", "pkg", "com.acme.shop"));
        assertThat(out).isEqualTo("class OrderItem -> table order_items in com/acme/shop");
    }

    @Test
    void builtInValuesNeedNoInput() {
        String out = TemplateEngine.render("{{today}} {{year}}", Map.of());
        LocalDate today = LocalDate.now();
        assertThat(out).isEqualTo(today + " " + today.getYear());
    }

    @Test
    void findsVariablesInOrderAndIgnoresBuiltInsAndGithubExpressions() {
        var vars = TemplateEngine.findVariables(List.of(
                "{{b}} {{a|pascal}} {{today}}", "${{ secrets.TOKEN }} {{b|kebab}} {{c}}"));
        assertThat(vars).containsExactly("b", "a", "c");
    }

    @Test
    void leavesNonPlaceholderBracesUntouched() {
        String template = "run: echo ${{ github.sha }} and {{ not valid! }}";
        assertThat(TemplateEngine.render(template, Map.of())).isEqualTo(template);
    }

    @Test
    void valuesContainingRegexCharactersAreInsertedLiterally() {
        assertThat(TemplateEngine.render("{{x}}", Map.of("x", "$1 \\ costs $5"))).isEqualTo("$1 \\ costs $5");
    }

    @Test
    void validateReportsUnknownFilters() {
        assertThat(TemplateEngine.validate("{{x|shout}}")).singleElement().asString().contains("shout");
        assertThat(TemplateEngine.validate("{{x|upper|kebab}}")).isEmpty();
    }

    @Test
    void renderFailsForMissingValue() {
        assertThatThrownBy(() -> TemplateEngine.render("{{missing}}", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
