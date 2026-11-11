package com.jobcrm.codegen.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ContractCheckTest {

  /** The <pre> block of MetricsLog.append's real contract, as it appears in the source. */
  private static final String APPEND_CONTRACT =
      """
      /**
       * Appends one record as a single UTF-8 JSON line.
       *
       * <ol>
       *   <li>If {@code record} is null, throw new IllegalArgumentException("record must not be null").
       * </ol>
       *
       * <pre>
       * "record must not be null"
       * "\\n"
       * "cannot append metrics to "
       * </pre>
       */
      """;

  private static final String APPEND_BODY =
      """
      if (record == null) {
        throw new IllegalArgumentException("record must not be null");
      }
      try {
        Files.writeString(file, json.writeValueAsString(record) + "\\n", StandardCharsets.UTF_8);
      } catch (IOException e) {
        throw new UncheckedIOException("cannot append metrics to " + file, e);
      }
      """;

  @Test
  void literalsAreThePreLinesWithQuotesKeptAndProseIgnored() {
    List<String> literals = ContractCheck.literals(APPEND_CONTRACT);

    assertThat(literals)
        .containsExactly("\"record must not be null\"", "\"\\n\"", "\"cannot append metrics to \"");
  }

  @Test
  void literalsKeepFirstAppearanceOrderWithoutDuplicates() {
    String javadoc = " * \"b\"\n * \"a\"\n * \"b\"\n * \"c\"\n";

    assertThat(ContractCheck.literals(javadoc)).containsExactly("\"b\"", "\"a\"", "\"c\"");
  }

  @Test
  void literalsStripSeveralLeadingAsterisksAndSurroundingSpace() {
    assertThat(ContractCheck.literals("   * * \"x\"   ")).containsExactly("\"x\"");
  }

  @Test
  void literalsDecodeHtmlEntitiesAndDecodeAmpersandLast() {
    // &amp;lt; must become the text &lt;, not be decoded a second time into <
    String javadoc = " * \"&lt;think&gt; &amp;lt; &#64;\"\n";

    assertThat(ContractCheck.literals(javadoc)).containsExactly("\"<think> &lt; @\"");
  }

  @Test
  void aLoneQuoteOrUnquotedLineIsNotALiteralButAnEmptyStringIs() {
    String javadoc = " * \"\n * plain text\n * \"half\n * half\"\n * \"\"\n";

    assertThat(ContractCheck.literals(javadoc)).containsExactly("\"\"");
  }

  @Test
  void literalsOfEmptyTextIsEmpty() {
    assertThat(ContractCheck.literals("")).isEmpty();
  }

  @Test
  void literalsRejectNull() {
    assertThatThrownBy(() -> ContractCheck.literals(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("javadoc must not be null");
  }

  @Test
  void aBodyThatHasEveryLiteralReportsNothingMissing() {
    assertThat(ContractCheck.missing(APPEND_CONTRACT, APPEND_BODY)).isEmpty();
  }

  @Test
  void aStringOneLetterOffIsReportedAsMissing() {
    String body = APPEND_BODY.replace("cannot append metrics to ", "cannot append metric to ");

    assertThat(ContractCheck.missing(APPEND_CONTRACT, body))
        .containsExactly("\"cannot append metrics to \"");
  }

  @Test
  void everyMissingLiteralIsReportedInContractOrder() {
    assertThat(ContractCheck.missing(APPEND_CONTRACT, "return;"))
        .containsExactly("\"record must not be null\"", "\"\\n\"", "\"cannot append metrics to \"");
  }

  @Test
  void aContractWithoutLiteralsNeverReportsAnything() {
    assertThat(ContractCheck.missing("/** Just prose. */", "anything")).isEmpty();
  }

  @Test
  void missingRejectsNullInputsWithTheirOwnMessages() {
    assertThatThrownBy(() -> ContractCheck.missing(null, "body"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("javadoc must not be null");
    assertThatThrownBy(() -> ContractCheck.missing("javadoc", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("body must not be null");
  }
}
