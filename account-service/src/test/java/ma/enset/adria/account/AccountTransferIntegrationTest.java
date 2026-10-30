package ma.enset.adria.account;

import ma.enset.adria.account.domain.AccountStatus;
import ma.enset.adria.account.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.math.BigDecimal;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {"eureka.client.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:account-transfer-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
class AccountTransferIntegrationTest {
    @Autowired AccountService service;
    private AccountView account(String balance) {
        return service.create(new CreateAccountRequest("TRANSFER-CUSTOMER","EUR",new BigDecimal(balance)));
    }

    @Test
    void transferUpdatesBothBalancesExactly() {
        var source=account("100"); var target=account("10");
        service.transfer(source.id(),target.id(),new BalanceOperationRequest(new BigDecimal("25.25"),"EUR"));
        assertThat(service.balance(source.id()).balance()).isEqualByComparingTo("74.75");
        assertThat(service.balance(target.id()).balance()).isEqualByComparingTo("35.25");
    }

    @Test
    void rejectionNeverDebitsSourceOrCreditsTarget() {
        var source=account("100"); var target=account("10");
        service.changeStatus(target.id(), AccountStatus.BLOCKED);
        assertThatThrownBy(() -> service.transfer(source.id(),target.id(),new BalanceOperationRequest(BigDecimal.TEN,"EUR")))
                .isInstanceOf(AccountRuleException.class);
        assertThat(service.balance(source.id()).balance()).isEqualByComparingTo("100");
        assertThat(service.balance(target.id()).balance()).isEqualByComparingTo("10");
        service.changeStatus(target.id(), AccountStatus.ACTIVE);
        assertThatThrownBy(() -> service.transfer(source.id(),target.id(),new BalanceOperationRequest(new BigDecimal("101"),"EUR")))
                .isInstanceOf(AccountRuleException.class);
        assertThat(service.balance(source.id()).balance()).isEqualByComparingTo("100");
        assertThat(service.balance(target.id()).balance()).isEqualByComparingTo("10");
        var full=account("999999999999999.9999");
        assertThatThrownBy(() -> service.transfer(source.id(),full.id(),new BalanceOperationRequest(BigDecimal.ONE,"EUR")))
                .isInstanceOf(AccountRuleException.class);
        assertThat(service.balance(source.id()).balance()).isEqualByComparingTo("100");
    }

    @Test
    void oppositeConcurrentTransfersKeepTheTotalAndAvoidDeadlock() throws Exception {
        var a=account("100"); var b=account("100");
        ExecutorService pool=Executors.newFixedThreadPool(2);
        CountDownLatch start=new CountDownLatch(1);
        try {
            Future<?> first=pool.submit(() -> { await(start); service.transfer(a.id(),b.id(),new BalanceOperationRequest(BigDecimal.TEN,"EUR")); });
            Future<?> second=pool.submit(() -> { await(start); service.transfer(b.id(),a.id(),new BalanceOperationRequest(BigDecimal.ONE,"EUR")); });
            start.countDown(); first.get(15,TimeUnit.SECONDS); second.get(15,TimeUnit.SECONDS);
            assertThat(service.balance(a.id()).balance()).isEqualByComparingTo("91");
            assertThat(service.balance(b.id()).balance()).isEqualByComparingTo("109");
        } finally { start.countDown(); pool.shutdownNow(); }
    }

    private void await(CountDownLatch latch) {
        try { if (!latch.await(5,TimeUnit.SECONDS)) throw new IllegalStateException("Timeout"); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
    }
}
