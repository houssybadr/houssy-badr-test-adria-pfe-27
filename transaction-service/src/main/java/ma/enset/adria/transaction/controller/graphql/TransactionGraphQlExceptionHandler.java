package ma.enset.adria.transaction.controller.graphql;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import jakarta.validation.ConstraintViolationException;
import ma.enset.adria.transaction.domain.TransactionException;
import org.springframework.graphql.execution.*;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindException;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class TransactionGraphQlExceptionHandler extends DataFetcherExceptionResolverAdapter {
    @Override
    protected GraphQLError resolveToSingleError(Throwable error, DataFetchingEnvironment environment) {
        if (error instanceof TransactionException business) {
            Map<String, Object> extensions = new LinkedHashMap<>();
            extensions.put("code", business.getCode());
            if (business.getTransactionId() != null) extensions.put("transactionId", business.getTransactionId().toString());
            ErrorType type = switch (business.getKind()) {
                case NOT_FOUND -> ErrorType.NOT_FOUND;
                case INVALID -> ErrorType.BAD_REQUEST;
                case UNAVAILABLE -> ErrorType.INTERNAL_ERROR;
            };
            return GraphqlErrorBuilder.newError(environment).errorType(type)
                    .message(business.getMessage()).extensions(extensions).build();
        }
        if (error instanceof ConstraintViolationException || error instanceof BindException || error instanceof IllegalArgumentException) {
            return GraphqlErrorBuilder.newError(environment).errorType(ErrorType.BAD_REQUEST)
                    .message("Parametres invalides").extensions(Map.of("code", "VALIDATION_ERROR")).build();
        }
        return null;
    }
}
