package ma.enset.adria.transaction.repository;

import ma.enset.adria.transaction.domain.BankTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<BankTransaction, UUID> {
    List<BankTransaction> findByAccountIdOrRecipientAccountIdOrderByCreatedAtDesc(UUID accountId, UUID recipientAccountId);
    List<BankTransaction> findAllByOrderByCreatedAtDesc();
}
