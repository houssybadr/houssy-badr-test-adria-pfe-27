package ma.enset.adria.transaction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import feign.Request;
import feign.Response;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import ma.enset.adria.transaction.client.AccountClient;
import ma.enset.adria.transaction.domain.*;
import ma.enset.adria.transaction.repository.TransactionRepository;
import ma.enset.adria.transaction.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"CONFIG_IMPORT=optional:classpath:/no-remote-config.properties", "spring.cloud.config.enabled=false", "spring.cloud.config.import-check.enabled=false", "eureka.client.enabled=false",
        "bank.transaction.max-amount=1000", "bank.account-service.url=http://localhost:1",
        "spring.datasource.url=jdbc:h2:mem:transaction-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureMockMvc
class TransactionIntegrationTest {
    @Autowired TransactionService service;
    @Autowired TransactionRepository repository;
    @Autowired CircuitBreakerRegistry breakers;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean AccountClient accounts;
    private final UUID source = UUID.randomUUID();
    private final UUID target = UUID.randomUUID();

    @BeforeEach
    void setup() {
        reset(accounts);
        breakers.circuitBreaker("accountReads").reset();
        repository.deleteAll();
        when(accounts.getAccount(any())).thenAnswer(call -> new AccountClient.Account(
                call.getArgument(0), "CUSTOMER", "ACTIVE", "EUR", new BigDecimal("2000")));
        when(accounts.getBalance(any())).thenAnswer(call -> new AccountClient.Balance(
                call.getArgument(0), new BigDecimal("2000"), "EUR"));
    }

    private CreateTransactionRequest request(String amount) {
        return new CreateTransactionRequest(source, new BigDecimal(amount), "EUR", "SHOP", "MA");
    }

    private FeignException remote(int status) {
        Request request = Request.create(Request.HttpMethod.GET, "http://account-service/test",
                Map.of(), null, StandardCharsets.UTF_8, null);
        return FeignException.errorStatus("account", Response.builder().request(request)
                .status(status).reason("test").headers(Map.of()).build());
    }

    @Test
    void createsReadsAndFiltersCompletedTransactions() throws Exception {
        String json = mapper.writeValueAsString(request("250"));
        String body = mvc.perform(post("/api/transactions").contentType("application/json").content(json))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andReturn().getResponse().getContentAsString();
        String id = mapper.readTree(body).get("id").asText();
        mvc.perform(get("/api/transactions/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(250));
        mvc.perform(get("/api/transactions").param("accountId", source.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/transactions").param("accountId", target.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        verify(accounts).getBalance(source);
        verify(accounts).debit(eq(source), eq(new AccountClient.Movement(new BigDecimal("250"), "EUR")));
    }

    @Test
    void acceptsConfiguredLimitAndRejectsAmountsAboveIt() {
        assertThat(service.create(request("1000")).getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThatThrownBy(() -> service.create(request("1000.01"))).isInstanceOf(TransactionException.class);
        assertThat(repository.count()).isEqualTo(1);
        verify(accounts, times(1)).debit(any(), any());
    }

    @Test
    void validatesPayloadBeforeCallingAccounts() throws Exception {
        for (String amount : new String[]{"0", "-1", "0.00001"}) {
            mvc.perform(post("/api/transactions").contentType("application/json")
                    .content(mapper.writeValueAsString(request(amount))))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/transactions").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        assertThat(repository.count()).isZero();
        verifyNoInteractions(accounts);
    }

    @Test
    void refusesMissingBlockedAndWrongCurrencyAccounts() {
        when(accounts.getAccount(source)).thenThrow(remote(404));
        assertThatThrownBy(() -> service.create(request("10"))).hasMessageContaining("introuvable");
        verify(accounts, times(1)).getAccount(source);
        doReturn(new AccountClient.Account(source,"C","BLOCKED","EUR",BigDecimal.TEN)).when(accounts).getAccount(source);
        assertThatThrownBy(() -> service.create(request("10"))).hasMessageContaining("actif");
        doReturn(new AccountClient.Account(source,"C","ACTIVE","USD",BigDecimal.TEN)).when(accounts).getAccount(source);
        assertThatThrownBy(() -> service.create(request("10"))).hasMessageContaining("devise");
        assertThat(repository.count()).isZero();
        verify(accounts, never()).debit(any(), any());
    }

    @Test
    void refusesInsufficientBalanceBeforeLaunchingMovement() {
        when(accounts.getBalance(source)).thenReturn(new AccountClient.Balance(source, BigDecimal.ONE, "EUR"));
        assertThatThrownBy(() -> service.create(request("10"))).hasMessageContaining("insuffisant");
        assertThat(repository.count()).isZero();
        verify(accounts, never()).debit(any(), any());
    }

    @Test
    void transferUsesOneAtomicRemoteOperationAndAppearsForBothAccounts() {
        var transfer = service.transfer(new CreateTransferRequest(source,target,new BigDecimal("50"),"EUR","MA"));
        assertThat(transfer.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(transfer.getMerchantCategory()).isEqualTo("TRANSFER");
        assertThat(service.findAll(source)).extracting(BankTransaction::getId).containsExactly(transfer.getId());
        assertThat(service.findAll(target)).extracting(BankTransaction::getId).containsExactly(transfer.getId());
        verify(accounts).transfer(new AccountClient.Transfer(source,target,new BigDecimal("50"),"EUR"));
        verify(accounts, never()).debit(any(), any());
    }

    @Test
    void rejectsSelfTransferAndBlockedRecipient() {
        assertThatThrownBy(() -> service.transfer(new CreateTransferRequest(source,source,BigDecimal.TEN,"EUR","MA")))
                .hasMessageContaining("differents");
        when(accounts.getAccount(target)).thenReturn(new AccountClient.Account(target,"C","BLOCKED","EUR",BigDecimal.ZERO));
        assertThatThrownBy(() -> service.transfer(new CreateTransferRequest(source,target,BigDecimal.TEN,"EUR","MA")))
                .hasMessageContaining("actif");
        assertThat(repository.count()).isZero();
        verify(accounts, never()).transfer(any());
    }

    @Test
    void retriesTransientReadFailuresButNeverDuplicatesTheDebit() {
        when(accounts.getAccount(source)).thenThrow(remote(503)).thenReturn(
                new AccountClient.Account(source,"C","ACTIVE","EUR",new BigDecimal("2000")));
        service.create(request("10"));
        verify(accounts, times(2)).getAccount(source);
        verify(accounts, times(1)).debit(any(), any());
    }

    @Test
    void openCircuitReturnsFallbackWithoutLaunchingMovement() throws Exception {
        breakers.circuitBreaker("accountReads").transitionToOpenState();
        mvc.perform(post("/api/transactions").contentType("application/json")
                .content(mapper.writeValueAsString(request("10"))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ACCOUNT_SERVICE_UNAVAILABLE"));
        verifyNoInteractions(accounts);
        assertThat(repository.count()).isZero();
    }

    @Test
    void confirmedRemoteRejectionIsRecordedAndNotRetried() {
        when(accounts.debit(any(), any())).thenThrow(remote(409));
        assertThatThrownBy(() -> service.create(request("10"))).isInstanceOf(TransactionException.class);
        assertThat(repository.findAll()).singleElement().extracting(BankTransaction::getStatus).isEqualTo(TransactionStatus.REJECTED);
        verify(accounts, times(1)).debit(any(), any());
    }

    @Test
    void uncertainRemoteWriteReturnsTransactionIdAndIsNeverRetried() throws Exception {
        when(accounts.debit(any(), any())).thenThrow(remote(504));
        String body = mvc.perform(post("/api/transactions").contentType("application/json")
                .content(mapper.writeValueAsString(request("10"))))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.transactionId").exists())
                .andReturn().getResponse().getContentAsString();
        JsonNode problem = mapper.readTree(body);
        assertThat(service.findById(UUID.fromString(problem.get("transactionId").asText())).getStatus()).isEqualTo(TransactionStatus.UNKNOWN);
        verify(accounts, times(1)).debit(any(), any());
    }

    @Test
    void unknownTransactionReturnsNotFound() throws Exception {
        mvc.perform(get("/api/transactions/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }
}
