package ma.enset.adria.account.web.rest;

import jakarta.validation.ConstraintViolationException;
import ma.enset.adria.account.service.AccountNotFoundException;
import ma.enset.adria.account.service.AccountRuleException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class AccountExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ProblemDetail> notFound(AccountNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(AccountRuleException.class)
    public ResponseEntity<ProblemDetail> business(AccountRuleException exception) {
        return problem(HttpStatus.CONFLICT, exception.getCode(), exception.getMessage());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> validation(ConstraintViolationException exception) {
        return problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Parametres invalides");
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String detail) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setProperty("code", code);
        return ResponseEntity.status(status).body(body);
    }
}
