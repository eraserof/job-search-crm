package com.jobcrm.core.application;

import com.jobcrm.core.domain.contact.ContactId;
import com.jobcrm.core.domain.interaction.Channel;
import com.jobcrm.core.domain.interaction.Direction;
import java.time.Instant;
import java.util.List;

/**
 * Command describing an interaction to record against an opportunity. The id and opportunity
 * reference are supplied by the service; this carries only the caller-provided facts.
 *
 * <p>{@code threadKey}, {@code subject}, and {@code externalId} are nullable.
 */
public record RecordInteractionCommand(
    List<ContactId> contacts,
    Channel channel,
    Direction direction,
    Instant occurredAt,
    String threadKey,
    String bodyText,
    String subject,
    String externalId) {}
