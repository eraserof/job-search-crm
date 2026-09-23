package com.jobcrm.core.domain.interaction;

import com.jobcrm.core.domain.opportunity.OpportunityId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Test-only in-memory {@link InteractionRepository}. */
public final class InMemoryInteractionRepository implements InteractionRepository {

  private final Map<InteractionId, Interaction> byId = new ConcurrentHashMap<>();

  @Override
  public Optional<Interaction> findById(InteractionId id) {
    return Optional.ofNullable(byId.get(id));
  }

  @Override
  public List<Interaction> findByOpportunity(OpportunityId opportunity) {
    Objects.requireNonNull(opportunity, "opportunity");
    return byId.values().stream().filter(i -> i.opportunity().equals(opportunity)).toList();
  }

  @Override
  public Optional<Interaction> findByExternalId(String externalId) {
    Objects.requireNonNull(externalId, "externalId");
    return byId.values().stream()
        .filter(i -> i.externalId().map(externalId::equals).orElse(false))
        .findFirst();
  }

  @Override
  public List<Interaction> findByThreadKey(String threadKey) {
    Objects.requireNonNull(threadKey, "threadKey");
    return byId.values().stream()
        .filter(i -> i.threadKey().map(threadKey::equals).orElse(false))
        .toList();
  }

  @Override
  public void save(Interaction interaction) {
    Objects.requireNonNull(interaction, "interaction");
    interaction
        .externalId()
        .ifPresent(
            ext ->
                byId.values().stream()
                    .filter(
                        other ->
                            !other.id().equals(interaction.id())
                                && other.externalId().map(ext::equals).orElse(false))
                    .findFirst()
                    .ifPresent(
                        other -> {
                          throw new IllegalStateException(
                              "externalId " + ext + " already used by " + other.id());
                        }));
    byId.put(interaction.id(), interaction);
  }

  public List<Interaction> all() {
    return List.copyOf(byId.values());
  }
}
