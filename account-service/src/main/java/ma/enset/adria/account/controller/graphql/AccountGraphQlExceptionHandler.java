package ma.enset.adria.account.controller.graphql;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import jakarta.validation.ConstraintViolationException;
import ma.enset.adria.account.service.AccountNotFoundException;
import ma.enset.adria.account.service.AccountRuleException;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindException;
import java.util.Map;

@Component
public class AccountGraphQlExceptionHandler extends DataFetcherExceptionResolverAdapter {
    @Override
    protected GraphQLError resolveToSingleError(Throwable exception, DataFetchingEnvironment environment) {
        ErrorType type;
        String code;
        String message;
        if (exception instanceof AccountNotFoundException) {
            type = ErrorType.NOT_FOUND;
            code = "ACCOUNT_NOT_FOUND";
            message = exception.getMessage();
        } else if (exception instanceof AccountRuleException rule) {
            type = ErrorType.BAD_REQUEST;
            code = rule.getCode();
            message = rule.getMessage();
        } else if (exception instanceof ConstraintViolationException
                || exception instanceof BindException || exception instanceof IllegalArgumentException) {
            type = ErrorType.BAD_REQUEST;
            code = "VALIDATION_ERROR";
            message = "Parametres invalides";
        } else {
            return null;
        }
        return GraphqlErrorBuilder.newError(environment).errorType(type).message(message)
                .extensions(Map.of("code", code)).build();
    }
}
