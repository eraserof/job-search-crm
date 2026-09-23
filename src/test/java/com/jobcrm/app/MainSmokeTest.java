package com.jobcrm.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobcrm.interfaces.cli.JobcrmCommand;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Verifies the Spring context boots cleanly and the Picocli root command bean is wired. Does not
 * run the command; that's covered later by CLI integration tests.
 */
@SpringBootTest(args = {"--help"})
class MainSmokeTest {

  @Autowired private JobcrmCommand rootCommand;

  @Test
  void contextLoadsWithRootCommandBeanWired() {
    assertThat(rootCommand).isNotNull();
  }
}
