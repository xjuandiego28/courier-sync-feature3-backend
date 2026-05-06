package com.ep18.couriersync.backend.common.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class ServiceOperations {

    private ServiceOperations() {
    }

    public static <T> void setIfPresent(T value, Consumer<T> setter) {
        Optional.ofNullable(value).ifPresent(setter);
    }

    public static <T> T valueOrDefault(T value, T defaultValue) {
        return Objects.requireNonNullElse(value, defaultValue);
    }

    public static double nvl(Double value) {
        return Objects.requireNonNullElse(value, 0.0);
    }

    public static int nvl(Integer value) {
        return Objects.requireNonNullElse(value, 0);
    }

    public static <T, ID> T findOrThrow(
            JpaRepository<T, ID> repository,
            ID id,
            Supplier<? extends RuntimeException> exceptionSupplier) {
        return repository.findById(id).orElseThrow(exceptionSupplier);
    }

    public static void rejectWhen(boolean condition, Supplier<? extends RuntimeException> exceptionSupplier) {
        Optional.of(condition)
                .filter(Boolean::booleanValue)
                .ifPresent(ignored -> {
                    throw exceptionSupplier.get();
                });
    }

    public static <T> void rejectDuplicatedChange(
            T value,
            T currentValue,
            BiPredicate<T, T> sameValue,
            Predicate<T> exists,
            Supplier<? extends RuntimeException> exceptionSupplier) {
        Optional.ofNullable(value)
                .filter(candidate -> !sameValue.test(candidate, currentValue))
                .filter(exists)
                .ifPresent(ignored -> {
                    throw exceptionSupplier.get();
                });
    }

    public static <T, ID> boolean deleteIfPresent(
            JpaRepository<T, ID> repository,
            ID id,
            Supplier<? extends RuntimeException> conflictSupplier) {
        return Optional.of(repository.existsById(id))
                .filter(Boolean::booleanValue)
                .map(ignored -> deleteExisting(repository, id, conflictSupplier))
                .orElse(false);
    }

    private static <T, ID> boolean deleteExisting(
            JpaRepository<T, ID> repository,
            ID id,
            Supplier<? extends RuntimeException> conflictSupplier) {
        try {
            repository.deleteById(id);
            return true;
        } catch (DataIntegrityViolationException e) {
            throw conflictSupplier.get();
        }
    }
}
