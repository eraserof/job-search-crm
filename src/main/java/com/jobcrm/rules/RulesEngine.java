package com.jobcrm.rules;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RulesEngine {
  private static final Logger log = LoggerFactory.getLogger(RulesEngine.class);

  RulesEngine() {}

  public void onTick(Instant now) {
    log.debug("rules tick at {}", now);
  }
}
