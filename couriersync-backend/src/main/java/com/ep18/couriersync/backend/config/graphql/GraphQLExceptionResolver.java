package com.ep18.couriersync.backend.config.graphql;

import com.ep18.couriersync.backend.common.exception.ConflictException;
import com.ep18.couriersync.backend.common.exception.DomainException;
import com.ep18.couriersync.backend.common.exception.NotFoundException;
import com.ep18.couriersync.backend.common.exception.ValidationException;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class GraphQLExceptionResolver extends DataFetcherExceptionResolverAdapter {

    private static final String VALIDATION_ERROR_CODE = "VALIDATION_ERROR";
    private static final String INTERNAL_ERROR_CODE = "INTERNAL_ERROR";
    private static final List<ValidationErrorMapping<? extends Throwable>> VALIDATION_MAPPINGS = List.of(
            new ValidationErrorMapping<>(
                    ConstraintViolationException.class,
                    GraphQLExceptionResolver::constraintViolationMessage),
            new ValidationErrorMapping<>(
                    MethodArgumentNotValidException.class,
                    GraphQLExceptionResolver::methodArgumentNotValidMessage)
    );
    private static final Map<Class<? extends DomainException>, ErrorType> DOMAIN_ERROR_TYPES = Map.of(
            NotFoundException.class, ErrorType.NOT_FOUND,
            ConflictException.class, ErrorType.BAD_REQUEST,
            ValidationException.class, ErrorType.BAD_REQUEST
    );

    @Override
    protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
        return resolveValidationError(ex, env)
                .or(() -> resolveDomainError(ex, env))
                .orElseGet(() -> internalError(ex, env));
    }

    private Optional<GraphQLError> resolveValidationError(Throwable ex, DataFetchingEnvironment env) {
        return VALIDATION_MAPPINGS.stream()
                .filter(mapping -> mapping.supports(ex))
                .findFirst()
                .map(mapping -> validationError(mapping.message(ex), env));
    }

    private Optional<GraphQLError> resolveDomainError(Throwable ex, DataFetchingEnvironment env) {
        return Optional.of(ex)
                .filter(DomainException.class::isInstance)
                .map(DomainException.class::cast)
                .map(domainException -> domainError(domainException, env));
    }

    private GraphQLError validationError(String message, DataFetchingEnvironment env) {
        return GraphqlErrorBuilder.newError(env)
                .errorType(ErrorType.BAD_REQUEST)
                .message(message)
                .extensions(Map.of("code", VALIDATION_ERROR_CODE))
                .build();
    }

    private GraphQLError domainError(DomainException ex, DataFetchingEnvironment env) {
        log.warn("GraphQL domain error on {}: {} ({})",
                env.getExecutionStepInfo().getPath(), ex.getMessage(), ex.getCode());

        return GraphqlErrorBuilder.newError(env)
                .errorType(domainErrorType(ex))
                .message(ex.getMessage())
                .extensions(Map.of("code", ex.getCode()))
                .build();
    }

    private ErrorType domainErrorType(DomainException ex) {
        return DOMAIN_ERROR_TYPES.entrySet().stream()
                .filter(entry -> entry.getKey().isInstance(ex))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(ErrorType.BAD_REQUEST);
    }

    private GraphQLError internalError(Throwable ex, DataFetchingEnvironment env) {
        log.error("GraphQL internal error on {}: {}", env.getExecutionStepInfo().getPath(), ex.toString());

        return GraphqlErrorBuilder.newError(env)
                .errorType(ErrorType.INTERNAL_ERROR)
                .message("Error interno. Intenta mas tarde")
                .extensions(Map.of("code", INTERNAL_ERROR_CODE))
                .build();
    }

    private static String constraintViolationMessage(ConstraintViolationException ex) {
        return ex.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining("; "));
    }

    private static String methodArgumentNotValidMessage(MethodArgumentNotValidException ex) {
        return ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining("; "));
    }

    private record ValidationErrorMapping<T extends Throwable>(
            Class<T> type,
            Function<T, String> messageFactory) {

        private boolean supports(Throwable ex) {
            return type.isInstance(ex);
        }

        private String message(Throwable ex) {
            return messageFactory.apply(type.cast(ex));
        }
    }
}
