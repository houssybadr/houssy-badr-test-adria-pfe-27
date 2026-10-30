package ma.enset.adria.account;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ma.enset.adria.account.service.AccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:account-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class AccountGraphQlIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired AccountService service;

    private JsonNode execute(String query, Map<String, Object> variables) throws Exception {
        String response = mvc.perform(post("/graphql").contentType("application/json")
                .content(mapper.writeValueAsString(Map.of("query", query, "variables", variables))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(response);
    }

    private String create() throws Exception {
        JsonNode response = execute("""
                mutation($input: CreateAccountInput!) {
                    createAccount(input: $input) { id balance currency status }
                }
                """, Map.of("input", Map.of("customerReference", "GRAPHQL-CUSTOMER", "currency", "EUR", "balance", "100.25")));
        assertThat(response.has("errors")).as(response.toString()).isFalse();
        assertThat(response.at("/data/createAccount/balance").asText()).isEqualTo("100.25");
        return response.at("/data/createAccount/id").asText();
    }

    @Test
    void graphqlAndRestShareTheSameAccounts() throws Exception {
        String id = create();
        mvc.perform(get("/api/accounts/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(100.25));
        JsonNode response = execute("""
                query($id: ID!) {
                    account(id: $id) { id customerReference }
                    accountBalance(id: $id) { balance currency }
                    accounts { id }
                }
                """, Map.of("id", id));
        assertThat(response.has("errors")).as(response.toString()).isFalse();
        assertThat(response.at("/data/account/id").asText()).isEqualTo(id);
        assertThat(response.at("/data/accountBalance/balance").asText()).isEqualTo("100.2500");
        assertThat(response.at("/data/accounts").toString()).contains(id);
    }

    @Test
    void graphqlMutationsReuseBalanceAndStatusRules() throws Exception {
        String id = create();
        JsonNode debit = execute("""
                mutation($id: ID!) { debitAccount(id: $id, input: {amount: "0.25", currency: "EUR"}) { balance } }
                """, Map.of("id", id));
        assertThat(debit.has("errors")).as(debit.toString()).isFalse();
        assertThat(service.balance(UUID.fromString(id)).balance()).isEqualByComparingTo("100");
        JsonNode credit = execute("""
                mutation($id: ID!) { creditAccount(id: $id, input: {amount: 0.10, currency: "EUR"}) { balance } }
                """, Map.of("id", id));
        assertThat(credit.has("errors")).as(credit.toString()).isFalse();
        assertThat(service.balance(UUID.fromString(id)).balance()).isEqualByComparingTo("100.10");
        JsonNode status = execute("""
                mutation($id: ID!) { changeAccountStatus(id: $id, status: BLOCKED) { status } }
                """, Map.of("id", id));
        assertThat(status.at("/data/changeAccountStatus/status").asText()).isEqualTo("BLOCKED");
        JsonNode blocked = execute("""
                mutation($id: ID!) { debitAccount(id: $id, input: {amount: "10", currency: "EUR"}) { balance } }
                """, Map.of("id", id));
        assertThat(blocked.at("/errors/0/extensions/code").asText()).isEqualTo("ACCOUNT_BLOCKED");
        assertThat(service.balance(UUID.fromString(id)).balance()).isEqualByComparingTo("100.10");
    }

    @Test
    void graphqlRejectsInvalidRequestsAndReportsMissingAccounts() throws Exception {
        String id = create();
        JsonNode negative = execute("""
                mutation($id: ID!) { debitAccount(id: $id, input: {amount: "-1", currency: "EUR"}) { balance } }
                """, Map.of("id", id));
        assertThat(negative.at("/errors/0/extensions/code").asText()).isEqualTo("VALIDATION_ERROR");
        JsonNode insufficient = execute("""
                mutation($id: ID!) { debitAccount(id: $id, input: {amount: "200", currency: "EUR"}) { balance } }
                """, Map.of("id", id));
        assertThat(insufficient.at("/errors/0/extensions/code").asText()).isEqualTo("INSUFFICIENT_FUNDS");
        JsonNode mismatch = execute("""
                mutation($id: ID!) { creditAccount(id: $id, input: {amount: "10", currency: "USD"}) { balance } }
                """, Map.of("id", id));
        assertThat(mismatch.at("/errors/0/extensions/code").asText()).isEqualTo("CURRENCY_MISMATCH");
        JsonNode missing = execute("query($id: ID!) { account(id: $id) { id } }", Map.of("id", UUID.randomUUID().toString()));
        assertThat(missing.at("/errors/0/extensions/code").asText()).isEqualTo("ACCOUNT_NOT_FOUND");
        JsonNode malformed = execute("query($id: ID!) { account(id: $id) { id } }", Map.of("id", "bad-id"));
        assertThat(malformed.at("/errors/0/extensions/code").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(service.balance(UUID.fromString(id)).balance()).isEqualByComparingTo("100.25");
    }

    @Test
    void decimalScalarRejectsInvalidOrImpreciseVariables() throws Exception {
        for (Object amount : new Object[]{"not-a-number", 0.1}) {
            JsonNode response = execute("""
                    mutation($input: CreateAccountInput!) { createAccount(input: $input) { id } }
                    """, Map.of("input", Map.of("customerReference", "C", "currency", "EUR", "balance", amount)));
            assertThat(response.has("errors")).as(response.toString()).isTrue();
        }
        mvc.perform(get("/graphiql")).andExpect(status().isTemporaryRedirect());
        mvc.perform(get("/graphiql").param("path", "/graphql")).andExpect(status().isOk());
    }
}
