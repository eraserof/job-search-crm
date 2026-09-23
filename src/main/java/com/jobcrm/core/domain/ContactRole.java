package com.jobcrm.core.domain;

/**
 * Role a {@code Contact} plays in a given {@code Opportunity}. A contact may play different roles
 * across different opportunities.
 */
public enum ContactRole {
  RECRUITER_INTERNAL,
  RECRUITER_EXTERNAL,
  HIRING_MANAGER,
  PANELIST,
  REFERRAL,
  OTHER
}
