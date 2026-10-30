package ma.enset.adria.transaction.controller.graphql;

import graphql.language.FloatValue;
import graphql.language.IntValue;
import graphql.language.StringValue;
import graphql.schema.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;
import java.math.BigDecimal;
import java.math.BigInteger;

@Configuration
public class TransactionGraphQlConfiguration {
    @Bean
    public RuntimeWiringConfigurer transactionScalars() {
        GraphQLScalarType decimal = GraphQLScalarType.newScalar()
                .name("BigDecimal")
                .description("Exact decimal; JSON variables and responses use strings")
                .coercing(new Coercing<BigDecimal, String>() {
                    @Override
                    public String serialize(Object value) {
                        if (value instanceof BigDecimal decimal) return decimal.toPlainString();
                        throw new CoercingSerializeException("Expected BigDecimal");
                    }

                    @Override
                    public BigDecimal parseValue(Object value) {
                        // Reject floating point JSON values: send monetary variables as strings.
                        if (value instanceof String || value instanceof BigDecimal || value instanceof BigInteger
                                || value instanceof Integer || value instanceof Long) {
                            try {
                                return new BigDecimal(value.toString());
                            } catch (NumberFormatException ignored) {
                                // Report a GraphQL input error below.
                            }
                        }
                        throw new CoercingParseValueException("Use a decimal string, for example \"100.25\"");
                    }

                    @Override
                    public BigDecimal parseLiteral(Object value) {
                        if (value instanceof IntValue integer) return new BigDecimal(integer.getValue());
                        if (value instanceof FloatValue decimal) return decimal.getValue();
                        if (value instanceof StringValue string) {
                            try {
                                return new BigDecimal(string.getValue());
                            } catch (NumberFormatException ignored) {
                                // Report a GraphQL input error below.
                            }
                        }
                        throw new CoercingParseLiteralException("Expected an exact decimal");
                    }
                }).build();
        return wiring -> wiring.scalar(decimal);
    }
}
