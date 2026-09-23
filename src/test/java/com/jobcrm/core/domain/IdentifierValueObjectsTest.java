package com.jobcrm.core.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Uniform contract tests for the strongly-typed identifier records. Each ID must reject a null
 * {@link UUID}, and each {@code newId()} factory must produce a distinct value.
 */
class IdentifierValueObjectsTest {

  private static Stream<Arguments> idFactories() {
    return Stream.of(
        Arguments.of(
            "OpportunityId",
            (Supplier<Object>) OpportunityId::newId,
            (Function<UUID, Object>) OpportunityId::new),
        Arguments.of(
            "ContactId",
            (Supplier<Object>) ContactId::newId,
            (Function<UUID, Object>) ContactId::new),
        Arguments.of(
            "CompanyId",
            (Supplier<Object>) CompanyId::newId,
            (Function<UUID, Object>) CompanyId::new),
        Arguments.of(
            "InteractionId",
            (Supplier<Object>) InteractionId::newId,
            (Function<UUID, Object>) InteractionId::new),
        Arguments.of(
            "EventId", (Supplier<Object>) EventId::newId, (Function<UUID, Object>) EventId::new),
        Arguments.of(
            "TaskId", (Supplier<Object>) TaskId::newId, (Function<UUID, Object>) TaskId::new),
        Arguments.of(
            "DocumentId",
            (Supplier<Object>) DocumentId::newId,
            (Function<UUID, Object>) DocumentId::new));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("idFactories")
  @DisplayName("newId() produces distinct values on successive calls")
  void newId_is_unique(String name, Supplier<Object> factory, Function<UUID, Object> ctor) {
    assertThat(factory.get()).isNotEqualTo(factory.get());
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("idFactories")
  @DisplayName("constructor rejects null UUID")
  void null_uuid_rejected(String name, Supplier<Object> factory, Function<UUID, Object> ctor) {
    assertThatThrownBy(() -> ctor.apply(null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("distinct IDs of the same type with equal UUID are equal")
  void value_equality_by_uuid() {
    UUID u = UUID.randomUUID();
    assertThat(new OpportunityId(u)).isEqualTo(new OpportunityId(u));
  }
}
