/**
 * Job Search Core — domain layer. Organised by aggregate:
 *
 * <ul>
 *   <li>{@link com.jobcrm.core.domain.company} — {@code Company} aggregate + identifier and
 *       repository port.
 *   <li>{@link com.jobcrm.core.domain.contact} — {@code Contact} aggregate, identifier, repository,
 *       {@code ContactRole}, and {@code EmailAddress}.
 *   <li>{@link com.jobcrm.core.domain.opportunity} — {@code Opportunity} aggregate root, its
 *       identifier, repository, {@code Stage} state machine, {@code ContactRef} scoped reference,
 *       and domain exceptions ({@code IllegalStageTransition}, {@code DuplicateContactAttachment}).
 *   <li>{@link com.jobcrm.core.domain.interaction} — {@code Interaction} child entity, identifier,
 *       repository, {@code Channel}, {@code Direction}.
 *   <li>{@link com.jobcrm.core.domain.event} — {@code Event} child entity, identifier, repository,
 *       {@code EventKind}.
 *   <li>{@link com.jobcrm.core.domain.task} — {@code Task} child entity, identifier, repository,
 *       {@code TaskType}, {@code TaskStatus}.
 *   <li>{@link com.jobcrm.core.domain.document} — {@code Document} entity, identifier, {@code
 *       DocumentKind}.
 *   <li>{@link com.jobcrm.core.domain.shared} — shared vocabulary that spans aggregates. Currently:
 *       {@code DraftMessageId} (aggregate lives in {@code messaging}; identifier lives here so
 *       {@code Task} can reference it without inverting bounded-context direction).
 * </ul>
 *
 * <p>Pure Java. No Spring, Flyway, JDBC, Jackson, Picocli, or other framework dependencies. Rules
 * enforced by ArchUnit.
 */
package com.jobcrm.core.domain;
