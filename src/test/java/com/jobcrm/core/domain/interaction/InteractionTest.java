package com.jobcrm.core.domain.interaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobcrm.core.domain.contact.ContactId;
import com.jobcrm.core.domain.opportunity.OpportunityId;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class InteractionTest {

  private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
  private static final OpportunityId OPP = OpportunityId.newId();
  private static final ContactId C1 = ContactId.newId();

  @Test
  void interaction_requires_at_least_one_contact() {
    assertThatThrownBy(
            () ->
                Interaction.record(
                    OPP, List.of(), Channel.EMAIL, Direction.INBOUND, T0, null, "hi", null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void interaction_contacts_list_is_unmodifiable() {
    Interaction i =
        Interaction.record(
            OPP, List.of(C1), Channel.EMAIL, Direction.INBOUND, T0, null, "hi", null, null);
    assertThatThrownBy(() -> i.contacts().add(ContactId.newId()))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void interaction_normalises_optional_fields_to_empty_when_blank() {
    Interaction i =
        Interaction.record(
            OPP, List.of(C1), Channel.EMAIL, Direction.INBOUND, T0, "   ", "hi", "  ", "\t\t");
    assertThat(i.threadKey()).isEmpty();
    assertThat(i.subject()).isEmpty();
    assertThat(i.externalId()).isEmpty();
  }
}
