package com.jobcrm.codegen.review;

import com.jobcrm.codegen.java.MethodLocator;
import java.util.List;
import java.util.Optional;

/**
 * Bug catcher, layer 1: checks a method that is now in the source against the contract in its own
 * Javadoc, with no model call. Only warns; nothing here changes a file or blocks a commit.
 */
public final class ContractReview {

  private ContractReview() {}

  /**
   * @param checked how many contract literals were looked for; 0 when the method has no Javadoc or
   *     its Javadoc lists no literals
   * @param missing the literals that do not appear in the body, in contract order
   */
  public record Result(int checked, List<String> missing) {
    public Result {
      missing = List.copyOf(missing);
    }
  }

  /**
   * Reads the Javadoc above the method matching {@code signature} in {@code source}, takes the
   * method's current body from the same text, and reports which contract literals the body lacks. A
   * method that cannot be located, or has no Javadoc, gives {@code Result(0, [])}.
   */
  public static Result check(String source, String signature) {
    Optional<MethodLocator.BodyRange> range = MethodLocator.find(source, signature);
    Optional<String> javadoc = MethodLocator.javadoc(source, signature);
    if (range.isEmpty() || javadoc.isEmpty()) {
      return new Result(0, List.of());
    }
    String body = source.substring(range.get().open() + 1, range.get().close());
    int checked = ContractCheck.literals(javadoc.get()).size();
    return new Result(checked, ContractCheck.missing(javadoc.get(), body));
  }

  /**
   * The text of {@code review.txt}: one header line, then one line per missing literal. Meant for a
   * person to read; it never says the code is wrong, only that the contract's text is not in it.
   */
  public static String report(String id, Result result) {
    StringBuilder out = new StringBuilder();
    out.append("review of ")
        .append(id)
        .append(": ")
        .append(result.missing().size())
        .append(" of ")
        .append(result.checked())
        .append(" contract literal(s) not found in the body\n");
    for (String literal : result.missing()) {
      out.append("  WARN ").append(literal).append('\n');
    }
    return out.toString();
  }
}
