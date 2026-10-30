package ma.enset.adria.account;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import ma.enset.adria.account.service.AccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false",
        "server.shutdown=immediate",
        "spring.datasource.url=jdbc:h2:mem:account-mcp-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class AccountMcpIntegrationTest {
    @LocalServerPort int port;
    @Autowired AccountService service;
    @Autowired ObjectMapper mapper;
    @Autowired TestRestTemplate rest;

    private McpSyncClient connect() {
        var transport = HttpClientSseClientTransport.builder("http://localhost:" + port).build();
        var client = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(15)).build();
        try {
            var initialized = client.initialize();
            assertThat(initialized.serverInfo().name()).isEqualTo("account-service");
            return client;
        } catch (RuntimeException | AssertionError error) {
            client.close();
            throw error;
        }
    }

    private JsonNode call(McpSyncClient client, String name, Map<String, Object> arguments) throws Exception {
        var result = client.callTool(new McpSchema.CallToolRequest(name, arguments));
        assertThat(result.isError()).as(result.toString()).isFalse();
        assertThat(result.content()).hasSize(1);
        return mapper.readTree(((McpSchema.TextContent) result.content().get(0)).text());
    }

    private String create(McpSyncClient client) throws Exception {
        return call(client, "createAccount", Map.of("customerReference", "MCP-CUSTOMER",
                "currency", "EUR", "balance", "100.25")).get("id").asText();
    }

    @Test
    void nativeMcpHandshakeDiscoveryAndAllToolsUseTheSameAccounts() throws Exception {
        try (var client = connect()) {
            assertThat(client.listTools().tools()).extracting(McpSchema.Tool::name)
                    .containsExactlyInAnyOrder("listAccounts", "getAccount", "getAccountBalance",
                            "createAccount", "changeAccountStatus", "debitAccount", "creditAccount");
            String id = create(client);
            assertThat(call(client, "getAccount", Map.of("accountId", id)).get("customerReference").asText())
                    .isEqualTo("MCP-CUSTOMER");
            assertThat(call(client, "getAccountBalance", Map.of("accountId", id)).get("balance").decimalValue())
                    .isEqualByComparingTo("100.25");
            assertThat(call(client, "listAccounts", Map.of()).toString()).contains(id);
            call(client, "debitAccount", Map.of("accountId", id, "amount", "0.25", "currency", "EUR"));
            call(client, "creditAccount", Map.of("accountId", id, "amount", "0.10", "currency", "EUR"));
            JsonNode viaRest = mapper.readTree(rest.getForObject("/api/accounts/" + id, String.class));
            assertThat(viaRest.get("balance").decimalValue()).isEqualByComparingTo("100.10");
            assertThat(service.balance(UUID.fromString(id)).balance()).isEqualByComparingTo("100.10");
            call(client, "changeAccountStatus", Map.of("accountId", id, "status", "BLOCKED"));
            var blocked = client.callTool(new McpSchema.CallToolRequest("debitAccount",
                    Map.of("accountId", id, "amount", "10", "currency", "EUR")));
            assertThat(blocked.isError()).as(blocked.toString()).isTrue();
            assertThat(service.balance(UUID.fromString(id)).balance()).isEqualByComparingTo("100.10");
            call(client, "changeAccountStatus", Map.of("accountId", id, "status", "ACTIVE"));
        }
    }

    @Test
    void nativeMcpReportsBusinessAndValidationErrorsWithoutChangingBalance() throws Exception {
        try (var client = connect()) {
            String id = create(client);
            for (String amount : new String[]{"0", "-1", "200", "not-a-number"}) {
                var result = client.callTool(new McpSchema.CallToolRequest("debitAccount",
                        Map.of("accountId", id, "amount", amount, "currency", "EUR")));
                assertThat(result.isError()).as(result.toString()).isTrue();
            }
            var mismatch = client.callTool(new McpSchema.CallToolRequest("creditAccount",
                    Map.of("accountId", id, "amount", "10", "currency", "USD")));
            assertThat(mismatch.isError()).as(mismatch.toString()).isTrue();
            for (String accountId : new String[]{UUID.randomUUID().toString(), "bad-id"}) {
                var result = client.callTool(new McpSchema.CallToolRequest("getAccount", Map.of("accountId", accountId)));
                assertThat(result.isError()).as(result.toString()).isTrue();
            }
            var invalid = client.callTool(new McpSchema.CallToolRequest("createAccount",
                    Map.of("customerReference", "", "currency", "EUR", "balance", "0")));
            assertThat(invalid.isError()).as(invalid.toString()).isTrue();
            assertThat(service.balance(UUID.fromString(id)).balance()).isEqualByComparingTo("100.25");
        }
    }
}
