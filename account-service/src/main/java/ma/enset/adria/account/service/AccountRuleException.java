package ma.enset.adria.account.service;

public class AccountRuleException extends RuntimeException {
    private final String code;

    public AccountRuleException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() { return code; }
}
