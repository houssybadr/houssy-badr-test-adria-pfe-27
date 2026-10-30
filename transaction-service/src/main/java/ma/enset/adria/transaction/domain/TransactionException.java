package ma.enset.adria.transaction.domain;

import java.util.UUID;

public class TransactionException extends RuntimeException {
    public enum Kind { NOT_FOUND, INVALID, UNAVAILABLE }
    private final Kind kind;
    private final String code;
    private final UUID transactionId;

    public TransactionException(Kind kind, String code, String message, UUID transactionId) {
        super(message);
        this.kind = kind;
        this.code = code;
        this.transactionId = transactionId;
    }

    public Kind getKind() { return kind; }
    public String getCode() { return code; }
    public UUID getTransactionId() { return transactionId; }
}
