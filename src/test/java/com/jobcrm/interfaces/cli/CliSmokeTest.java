package com.jobcrm.interfaces.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobcrm.app.Main;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import picocli.CommandLine;
import picocli.CommandLine.IFactory;

/**
 * Smoke test for the Picocli CLI wiring. Boots the real Spring context (which runs Flyway against
 * the local SQLite file) and executes a handful of command lines end-to-end, asserting exit code 0
 * and sane output. This verifies the Picocli-Spring factory can instantiate every command and its
 * injected dependencies.
 *
 * <p>{@code classes = Main.class} is required because this test lives in {@code
 * com.jobcrm.interfaces.cli}, a sibling of {@code com.jobcrm.app} where
 * {@code @SpringBootApplication} lives — so the default upward package search can't find the
 * configuration.
 */
@SpringBootTest(classes = Main.class)
class CliSmokeTest {

  @Autowired IFactory factory;
  @Autowired JobcrmCommand rootCommand;

  private String run(int expectedExit, String... args) {
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    PrintStream original = System.out;
    PrintStream capture = new PrintStream(buf, true, StandardCharsets.UTF_8);
    System.setOut(capture);
    int exit;
    try {
      CommandLine cmd = new CommandLine(rootCommand, factory);
      exit = cmd.execute(args);
    } finally {
      System.setOut(original);
    }
    String out = buf.toString(StandardCharsets.UTF_8);
    assertThat(exit)
        .as("exit code for %s -> %s", String.join(" ", args), out)
        .isEqualTo(expectedExit);
    return out;
  }

  @Test
  void companyAndOpportunityAndContactFlow() {
    String unique = "Acme-" + System.nanoTime();

    run(0, "company", "new", "--name", unique);

    String companies = run(0, "company", "list");
    assertThat(companies).contains("REF", "NAME", unique);

    run(0, "opp", "new", "--company", unique, "--role", "Engineer");

    String opps = run(0, "opp", "list");
    assertThat(opps).contains("REF", "COMPANY", "ROLE", "STAGE", "Engineer");

    String contactName = "Jane-" + System.nanoTime();
    String contactEmail = "jane" + System.nanoTime() + "@example.com";
    run(0, "contact", "new", "--name", contactName, "--email", contactEmail);

    String contacts = run(0, "contact", "list");
    assertThat(contacts).contains("REF", "NAME", "EMAILS", contactName);
  }

  @Test
  void statusResolvesCreatedOpportunity() {
    String unique = "Globex-" + System.nanoTime();

    // Create an opp as JSON so we can parse its id out of the output. --json is a leaf option, so
    // it goes after the subcommand.
    String json = run(0, "opp", "new", "--company", unique, "--role", "SRE", "--json");
    assertThat(json).contains("\"id\"");

    String id = extractId(json);
    assertThat(id).isNotBlank();

    String status = run(0, "status", id);
    assertThat(status).contains("Stage", "APPLIED", "SRE", unique);
  }

  @Test
  void authStatusReportsIntegrations() {
    String out = run(0, "auth", "status");
    assertThat(out).contains("Gmail", "LLM", "Calendar", "not configured");
  }

  @Test
  void initPrintsOk() {
    String out = run(0, "init", "--json");
    assertThat(out).contains("status", "ok");
  }

  private static String extractId(String json) {
    int idx = json.indexOf("\"id\"");
    int colon = json.indexOf(':', idx);
    int firstQuote = json.indexOf('"', colon + 1);
    int secondQuote = json.indexOf('"', firstQuote + 1);
    return json.substring(firstQuote + 1, secondQuote);
  }
}
