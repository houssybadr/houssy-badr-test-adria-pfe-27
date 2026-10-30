package ma.enset.adria.transaction.controller.rest;

import jakarta.validation.ConstraintViolationException;
import ma.enset.adria.transaction.domain.TransactionException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class TransactionExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(TransactionException.class)
    public ResponseEntity<ProblemDetail> business(TransactionException exception) {
        HttpStatus status = switch (exception.getKind()) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case INVALID -> HttpStatus.CONFLICT;
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
        body.setProperty("code", exception.getCode());
        if (exception.getTransactionId() != null) body.setProperty("transactionId", exception.getTransactionId());
        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> validation(ConstraintViolationException exception) {
        return ResponseEntity.badRequest().body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Parametres invalides"));
    }
}
