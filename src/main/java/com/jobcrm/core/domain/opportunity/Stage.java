package com.jobcrm.core.domain.opportunity;

import java.util.EnumSet;
import java.util.Set;

/**
 * Lifecycle stage of an {@code Opportunity}. The state machine is enforced by {@link
 * #canTransitionTo(Stage)}; terminal states are those from which no forward progress is possible
 * (they may still transition to nothing, or in the case of {@code GHOSTED}, back to mid-loop stages
 * if re-engagement succeeds — {@code GHOSTED} is *not* terminal for that reason).
 */
public enum Stage {
  APPLIED,
  RECRUITER_SCREEN,
  HM_SCREEN,
  PANEL,
  ONSITE,
  OFFER,
  REJECTED,
  GHOSTED,
  WITHDRAWN;

  /** Terminal stages disallow any further transition. {@code GHOSTED} is not terminal. */
  public boolean isTerminal() {
    return this == REJECTED || this == WITHDRAWN;
  }

  /** Returns {@code true} iff transitioning from {@code this} to {@code next} is permitted. */
  public boolean canTransitionTo(Stage next) {
    if (next == null) {
      return false;
    }
    return allowedNextStages().contains(next);
  }

  private Set<Stage> allowedNextStages() {
    return switch (this) {
      case APPLIED -> EnumSet.of(RECRUITER_SCREEN, HM_SCREEN, REJECTED, GHOSTED, WITHDRAWN);
      case RECRUITER_SCREEN -> EnumSet.of(HM_SCREEN, PANEL, ONSITE, REJECTED, GHOSTED, WITHDRAWN);
      case HM_SCREEN -> EnumSet.of(PANEL, ONSITE, REJECTED, GHOSTED, WITHDRAWN);
      case PANEL -> EnumSet.of(ONSITE, OFFER, REJECTED, GHOSTED, WITHDRAWN);
      case ONSITE -> EnumSet.of(OFFER, REJECTED, GHOSTED, WITHDRAWN);
      case OFFER -> EnumSet.of(REJECTED, WITHDRAWN);
      case REJECTED -> EnumSet.noneOf(Stage.class);
      case GHOSTED -> EnumSet.of(RECRUITER_SCREEN, HM_SCREEN);
      case WITHDRAWN -> EnumSet.noneOf(Stage.class);
    };
  }
}
