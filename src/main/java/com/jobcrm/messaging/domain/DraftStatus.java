package com.jobcrm.messaging.domain;

/**
 * Lifecycle status of a {@code DraftMessage}.
 *
 * <p>State machine (enforced by {@code DraftMessage} operations; see {@code domain-model.md} §
 * DraftMessage):
 *
 * <pre>
 *   PENDING_REVIEW → APPROVED → SENT
 *   PENDING_REVIEW → DISCARDED
 * </pre>
 */
public enum DraftStatus {
  PENDING_REVIEW,
  APPROVED,
  DISCARDED,
  SENT
}
