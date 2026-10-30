package ma.enset.adria.account;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolationException;
import ma.enset.adria.account.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:account-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class AccountIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired AccountService service;

    private AccountView account(String balance) {
        return service.create(new CreateAccountRequest("CLIENT-001", "EUR", new BigDecimal(balance)));
    }

    @Test
    void createReadListAndBalance() throws Exception {
        String response = mvc.perform(post("/api/accounts").contentType("application/json")
                .content(""" 
                    {"customerReference":"CLIENT-REST","currency":"EUR","balance":100.25}
                    """))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();
        String id = mapper.readTree(response).get("id").asText();
        mvc.perform(get("/api/accounts/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.customerReference").value("CLIENT-REST"));
        mvc.perform(get("/api/accounts/" + id + "/balance")).andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(100.25))
                .andExpect(jsonPath("$.currency").value("EUR"));
        mvc.perform(get("/api/accounts")).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + id + "')]").isNotEmpty());
    }

    @Test
    void creditAndDebitKeepExactDecimalBalance() throws Exception {
        var account = account("0.10");
        mvc.perform(post("/api/accounts/" + account.id() + "/credit")
                .contentType("application/json").content("{\"amount\":0.20,\"currency\":\"EUR\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.balance").value(0.30));
        mvc.perform(post("/api/accounts/" + account.id() + "/debit")
                .contentType("application/json").content("{\"amount\":0.10,\"currency\":\"EUR\"}"))
                .andExpect(status().isOk());
        assertThat(service.balance(account.id()).balance()).isEqualByComparingTo("0.20");
    }

    @Test
    void refusesOverdraftAndCurrencyMismatchWithoutChangingBalance() throws Exception {
        var account = account("100");
        mvc.perform(post("/api/accounts/" + account.id() + "/debit").contentType("application/json")
                .content("{\"amount\":101,\"currency\":\"EUR\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
        mvc.perform(post("/api/accounts/" + account.id() + "/credit").contentType("application/json")
                .content("{\"amount\":10,\"currency\":\"USD\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CURRENCY_MISMATCH"));
        assertThat(service.balance(account.id()).balance()).isEqualByComparingTo("100");
    }

    @Test
    void blockedAccountRefusesOperationsAndCanBeReactivated() throws Exception {
        var account = account("100");
        mvc.perform(patch("/api/accounts/" + account.id() + "/status").contentType("application/json")
                .content("{\"status\":\"BLOCKED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("BLOCKED"));
        for (String operation : new String[]{"debit", "credit"}) {
            mvc.perform(post("/api/accounts/" + account.id() + "/" + operation).contentType("application/json")
                    .content("{\"amount\":10,\"currency\":\"EUR\"}"))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ACCOUNT_BLOCKED"));
        }
        assertThat(service.balance(account.id()).balance()).isEqualByComparingTo("100");
        mvc.perform(patch("/api/accounts/" + account.id() + "/status").contentType("application/json")
                .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk());
        service.debit(account.id(), new BalanceOperationRequest(BigDecimal.TEN, "EUR"));
        assertThat(service.balance(account.id()).balance()).isEqualByComparingTo("90");
    }

    @Test
    void invalidPayloadsReturnBadRequest() throws Exception {
        for (String payload : new String[]{
                "{}",
                "{\"customerReference\":\" \",\"currency\":\"EUR\",\"balance\":0}",
                "{\"customerReference\":\"C\",\"currency\":\"EUR\",\"balance\":-1}",
                "{\"customerReference\":\"C\",\"currency\":\"eur\",\"balance\":0}",
                "{\"customerReference\":\"C\",\"currency\":\"EUR\",\"balance\":0.00001}"}) {
            mvc.perform(post("/api/accounts").contentType("application/json").content(payload))
                    .andExpect(status().isBadRequest());
        }
        var account = account("100");
        for (String amount : new String[]{"0", "-1", "null"}) {
            mvc.perform(post("/api/accounts/" + account.id() + "/debit").contentType("application/json")
                    .content("{\"amount\":" + amount + ",\"currency\":\"EUR\"}"))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(patch("/api/accounts/" + account.id() + "/status").contentType("application/json")
                .content("{}" )).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/accounts/" + account.id() + "/status").contentType("application/json")
                .content("{\"status\":\"OTHER\"}" )).andExpect(status().isBadRequest());
        assertThat(service.balance(account.id()).balance()).isEqualByComparingTo("100");
    }

    @Test
    void unknownAccountAndMalformedUuidAreHandled() throws Exception {
        mvc.perform(get("/api/accounts/" + UUID.randomUUID())).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
        mvc.perform(get("/api/accounts/not-a-uuid")).andExpect(status().isBadRequest());
    }

    @Test
    void serviceValidationAlsoProtectsFutureGraphqlAndMcpCalls() {
        var account = account("100");
        assertThatThrownBy(() -> service.debit(account.id(), new BalanceOperationRequest(BigDecimal.ZERO, "EUR")))
                .isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> service.create(new CreateAccountRequest("", "EUR", BigDecimal.ZERO)))
                .isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> service.create(new CreateAccountRequest("C", "ZZZ", BigDecimal.ZERO)))
                .isInstanceOf(AccountRuleException.class).hasMessageContaining("ISO 4217");
    }

    @Test
    void concurrentDebitsCannotSpendTheSameBalanceTwice() throws Exception {
        var account = account("100");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> debit = () -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timeout");
            try {
                service.debit(account.id(), new BalanceOperationRequest(new BigDecimal("80"), "EUR"));
                return true;
            } catch (AccountRuleException exception) {
                assertThat(exception.getCode()).isEqualTo("INSUFFICIENT_FUNDS");
                return false;
            }
        };
        try {
            Future<Boolean> first = pool.submit(debit);
            Future<Boolean> second = pool.submit(debit);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            boolean a = first.get(15, TimeUnit.SECONDS);
            boolean b = second.get(15, TimeUnit.SECONDS);
            assertThat(a ^ b).isTrue();
            assertThat(service.balance(account.id()).balance()).isEqualByComparingTo("20");
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void balanceOverflowIsRejectedWithoutChangingData() {
        var account = account("999999999999999.9999");
        assertThatThrownBy(() -> service.credit(account.id(), new BalanceOperationRequest(BigDecimal.ONE, "EUR")))
                .isInstanceOf(AccountRuleException.class);
        assertThat(service.balance(account.id()).balance()).isEqualByComparingTo("999999999999999.9999");
    }
}
