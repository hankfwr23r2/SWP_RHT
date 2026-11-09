package com.example.racehorse_transport.entity;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class FinancialNormalizationTest {
    @Test
    void quotationMovesOnlyFinancialArraysAndPreservesInsuranceSnapshot() throws Exception {
        String source = """
                {"insuranceSnapshot":[{"horseId":"H-001","insurance":{"policyNo":"P-001","pdf":"quote.pdf"}}],
                 "lines":[{"label":"Vận chuyển","detail":null,"calculation":"2 x 10","amount":9007199254740993.25},
                          {"label":"Miễn phí","amount":0}],
                 "adjustments":[{"label":"Ưu đãi","amount":-100.25}],"issuedBy":"Manager"}
                """;
        Quotation quotation = new Quotation();

        quotation.setQuoteLinesJson(source);

        assertThat(quotation.isLinesNormalized()).isTrue();
        assertThat(quotation.getLineItems()).hasSize(3);
        assertThat(quotation.getLineItems().get(0).getAmount()).isEqualByComparingTo(new BigDecimal("9007199254740993.25"));
        assertThat(quotation.getLineItems().get(0).getPresentFields()).isEqualTo(15);
        assertThat(quotation.getLineItems().get(1).getPresentFields()).isEqualTo(9);
        assertThat(quotation.getLineItems()).allSatisfy(line -> assertThat(line.getQuotation()).isSameAs(quotation));
        JsonNode stored = json(quotation.getStoredQuoteLinesJson());
        assertThat(stored.path("lines").isEmpty()).isTrue();
        assertThat(stored.path("adjustments").isEmpty()).isTrue();
        assertThat(stored.path("insuranceSnapshot")).isEqualTo(json(source).path("insuranceSnapshot"));
        assertThat(json(quotation.getQuoteLinesJson())).isEqualTo(json(source));
    }

    @Test
    void quotationRetainsMissingNullAndLegacyArrayShapes() throws Exception {
        for (String source : new String[]{"{\"lines\":null}", "{\"adjustments\":[]}",
                "[{\"label\":null,\"amount\":1.25}]", "[]", "{}"}) {
            Quotation quotation = new Quotation();
            quotation.setQuoteLinesJson(source);

            assertThat(quotation.isLinesNormalized()).isTrue();
            assertThat(json(quotation.getQuoteLinesJson())).isEqualTo(json(source));
        }
    }

    @Test
    void quotationUpdatesExistingLineRowsWithoutReplacingTheirDatabaseIdentity() {
        Quotation quotation = new Quotation();
        quotation.setQuoteLinesJson("{\"lines\":[{\"label\":\"A\",\"amount\":10},{\"label\":\"B\",\"amount\":20}],\"adjustments\":[{\"amount\":-1}]}");
        QuotationLine first = quotation.getLineItems().get(0);
        first.setId(77L);
        QuotationLine adjustment = quotation.getLineItems().get(2);
        adjustment.setId(79L);

        quotation.setQuoteLinesJson("{\"lines\":[{\"label\":\"Updated\",\"amount\":30}],\"adjustments\":[{\"amount\":-2}]}");

        assertThat(quotation.getLineItems()).containsExactly(first, adjustment);
        assertThat(first.getId()).isEqualTo(77L);
        assertThat(first.getLabel()).isEqualTo("Updated");
        assertThat(adjustment.getId()).isEqualTo(79L);
    }

    @Test
    void quotationRetainsCompleteLegacyPayloadWhenARecordCannotBeRepresented() {
        for (String source : new String[]{
                "{\"lines\":[{\"amount\":10},{\"amount\":20,\"unknown\":true}],\"insuranceSnapshot\":[1]}",
                "{\"lines\":[{\"amount\":\"10\"}]}",
                "{\"lines\":{\"amount\":10}}",
                "{\"lines\":[],\"lines\":[{\"amount\":1}]}",
                "[] []"}) {
            Quotation quotation = new Quotation();
            quotation.setQuoteLinesJson(source);

            assertThat(quotation.isLinesNormalized()).isFalse();
            assertThat(quotation.getLineItems()).isEmpty();
            assertThat(quotation.getStoredQuoteLinesJson()).isEqualTo(source);
            assertThat(quotation.getQuoteLinesJson()).isEqualTo(source);
        }
    }

    @Test
    void incidentPreservesExpenseContractWithExactAmountsAndStableLegacyId() throws Exception {
        String source = """
                [{"id":"expense-1","category":"vet_fee","label":"Khám ngựa","photo":"invoice.jpg",
                  "amount":9007199254740993.25,"payer":"customer","at":1780000000123,"by":"Driver"},
                 {"label":null,"amount":null}]
                """;
        Incident incident = new Incident();

        incident.setExpensesJson(source);

        assertThat(incident.isExpensesNormalized()).isTrue();
        assertThat(incident.getStoredExpensesJson()).isEqualTo("[]");
        assertThat(incident.getExpenseItems()).hasSize(2);
        IncidentExpense first = incident.getExpenseItems().get(0);
        assertThat(first.getIncident()).isSameAs(incident);
        assertThat(first.getExpenseId()).isEqualTo("expense-1");
        assertThat(first.getPresentFields()).isEqualTo(255);
        assertThat(first.getOccurredAtEpochMs()).isEqualTo(1780000000123L);
        assertThat(first.getAmount()).isEqualByComparingTo(new BigDecimal("9007199254740993.25"));
        assertThat(incident.getExpenseItems().get(1).getPresentFields()).isEqualTo(20);
        assertThat(json(incident.getExpensesJson())).isEqualTo(json(source));
    }

    @Test
    void removingEarlierExpenseRetainsSurvivingRowAndAddingExpenseCreatesOnlyNewRow() {
        Incident incident = new Incident();
        incident.setExpensesJson("[{\"id\":\"a\",\"amount\":10},{\"id\":\"b\",\"amount\":20}]");
        IncidentExpense survivor = incident.getExpenseItems().get(1);
        survivor.setId(52L);

        incident.setExpensesJson("[{\"id\":\"b\",\"amount\":25},{\"id\":\"c\",\"amount\":30}]");

        assertThat(incident.getExpenseItems()).hasSize(2);
        assertThat(incident.getExpenseItems().get(0)).isSameAs(survivor);
        assertThat(survivor.getId()).isEqualTo(52L);
        assertThat(survivor.getExpensePosition()).isZero();
        assertThat(survivor.getAmount()).isEqualByComparingTo("25");
        assertThat(incident.getExpenseItems().get(1).getId()).isNull();
    }

    @Test
    void duplicateLegacyExpenseIdsArePreservedInsteadOfDroppingRows() throws Exception {
        Incident incident = new Incident();
        String source = "[{\"id\":\"a\",\"amount\":1},{\"id\":\"a\",\"amount\":2}]";
        incident.setExpensesJson(source);
        IncidentExpense first = incident.getExpenseItems().get(0);
        IncidentExpense second = incident.getExpenseItems().get(1);

        incident.setExpensesJson(source);

        assertThat(incident.getExpenseItems()).containsExactly(first, second);
        assertThat(json(incident.getExpensesJson())).isEqualTo(json(source));
    }

    @Test
    void unsupportedExpenseDataRetainsWholeLegacyPayloadWithoutPartialRows() {
        for (String source : new String[]{"[{\"amount\":10},{\"unknown\":true}]",
                "[{\"amount\":\"10\"}]", "[{\"at\":1.5}]", "[{\"at\":9223372036854775808}]", "{}", "not json"}) {
            Incident incident = new Incident();
            incident.setExpensesJson(source);

            assertThat(incident.isExpensesNormalized()).isFalse();
            assertThat(incident.getExpenseItems()).isEmpty();
            assertThat(incident.getExpensesJson()).isEqualTo(source);
        }
    }

    @Test
    void persistenceCallbacksNormalizeBuilderPayloadsWithoutRepositories() {
        Quotation quotation = Quotation.builder().quoteLinesJson("{\"lines\":[{\"amount\":1}]}").build();
        Incident incident = Incident.builder().expensesJson("[{\"id\":\"a\",\"amount\":2}]").build();

        quotation.normalizeBuilderLines();
        incident.normalizeBuilderExpenses();

        assertThat(quotation.isLinesNormalized()).isTrue();
        assertThat(quotation.getLineItems()).hasSize(1);
        assertThat(incident.isExpensesNormalized()).isTrue();
        assertThat(incident.getExpenseItems()).hasSize(1);
    }

    @Test
    void normalizedStorageMetadataDoesNotLeakIntoExistingJsonContract() {
        Quotation quotation = new Quotation();
        quotation.setQuoteLinesJson("{\"lines\":[{\"amount\":10}]}");
        Incident incident = new Incident();
        incident.setExpensesJson("[{\"id\":\"a\",\"amount\":20}]");

        JsonNode quote = FinancialJsonSupport.JSON.valueToTree(quotation);
        JsonNode expense = FinancialJsonSupport.JSON.valueToTree(incident);

        assertThat(quote.has("lineItems")).isFalse();
        assertThat(quote.has("linesNormalized")).isFalse();
        assertThat(quote.has("storedQuoteLinesJson")).isFalse();
        assertThat(quote.has("version")).isFalse();
        assertThat(quote.path("quoteLinesJson").asText()).contains("10");
        assertThat(expense.has("expenseItems")).isFalse();
        assertThat(expense.has("expensesNormalized")).isFalse();
        assertThat(expense.has("storedExpensesJson")).isFalse();
        assertThat(expense.has("version")).isFalse();
        assertThat(expense.path("expensesJson").asText()).contains("20");
    }

    @Test
    void clearingFinancialDataRemovesChildrenAndKeepsNullDistinctFromEmptyArrays() {
        Quotation quotation = new Quotation();
        quotation.setQuoteLinesJson("[{\"amount\":1}]");
        Incident incident = new Incident();
        incident.setExpensesJson("[{\"amount\":2}]");

        quotation.setQuoteLinesJson(null);
        incident.setExpensesJson(null);

        assertThat(quotation.getLineItems()).isEmpty();
        assertThat(quotation.getQuoteLinesJson()).isNull();
        assertThat(incident.getExpenseItems()).isEmpty();
        assertThat(incident.getExpensesJson()).isNull();
        incident.setExpensesJson("[]");
        assertThat(incident.getExpensesJson()).isEqualTo("[]");
    }

    private static JsonNode json(String source) throws Exception {
        return FinancialJsonSupport.JSON.readTree(source);
    }
}
