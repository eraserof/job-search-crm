package com.jobcrm.core.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jobcrm.core.domain.company.Company;
import com.jobcrm.core.domain.company.CompanyId;
import com.jobcrm.core.domain.company.CompanyRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class CompanyServiceTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  private CompanyRepository repo;
  private CompanyService service;

  @BeforeEach
  void setUp() {
    repo = Mockito.mock(CompanyRepository.class);
    service = new CompanyService(repo, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void create_persists_and_returns_id() {
    CompanyId id = service.create("Stripe");
    ArgumentCaptor<Company> saved = ArgumentCaptor.forClass(Company.class);
    verify(repo).save(saved.capture());
    assertThat(saved.getValue().name()).isEqualTo("Stripe");
    assertThat(saved.getValue().id()).isEqualTo(id);
  }

  @Test
  void renameTo_unknown_id_throws_and_does_not_save() {
    CompanyId missing = CompanyId.newId();
    when(repo.findById(missing)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.renameTo(missing, "New")).isInstanceOf(UnknownAggregate.class);
    verify(repo, never()).save(any());
  }

  @Test
  void findOrCreateByName_returns_existing_when_present() {
    Company existing = Company.create("Stripe", NOW);
    when(repo.findByName("Stripe")).thenReturn(Optional.of(existing));

    CompanyId id = service.findOrCreateByName("Stripe");

    assertThat(id).isEqualTo(existing.id());
    verify(repo, never()).save(any());
  }

  @Test
  void findOrCreateByName_creates_when_absent() {
    when(repo.findByName("Datadog")).thenReturn(Optional.empty());
    CompanyId id = service.findOrCreateByName("Datadog");
    ArgumentCaptor<Company> saved = ArgumentCaptor.forClass(Company.class);
    verify(repo).save(saved.capture());
    assertThat(saved.getValue().id()).isEqualTo(id);
    assertThat(saved.getValue().name()).isEqualTo("Datadog");
  }
}
