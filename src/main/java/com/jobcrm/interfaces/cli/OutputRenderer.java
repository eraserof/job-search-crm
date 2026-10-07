package com.jobcrm.interfaces.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.PrintStream;
import java.util.List;

/**
 * Renders command output as either an aligned text table or pretty-printed JSON. Centralises the
 * two output modes so every {@code list}/{@code show} command behaves consistently (see ui-cli.md §
 * Output Conventions).
 */
public final class OutputRenderer {

  private static final ObjectMapper JSON =
      new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

  private final PrintStream out;

  public OutputRenderer(PrintStream out) {
    this.out = out;
  }

  public OutputRenderer() {
    this(System.out);
  }

  /** Renders a value as pretty JSON. */
  public void json(Object value) {
    try {
      out.println(JSON.writeValueAsString(value));
    } catch (Exception e) {
      throw new RuntimeException("failed to render JSON", e);
    }
  }

  /**
   * Renders a table with the given headers and rows. Column widths are sized to the widest cell. An
   * empty row list prints a header plus a "(none)" line so output is never silent.
   */
  public void table(List<String> headers, List<List<String>> rows) {
    int cols = headers.size();
    int[] widths = new int[cols];
    for (int i = 0; i < cols; i++) {
      widths[i] = headers.get(i).length();
    }
    for (List<String> row : rows) {
      for (int i = 0; i < cols; i++) {
        widths[i] = Math.max(widths[i], cell(row, i).length());
      }
    }

    out.println(formatRow(headers, widths));
    if (rows.isEmpty()) {
      out.println("(none)");
      return;
    }
    for (List<String> row : rows) {
      out.println(formatRow(row, widths));
    }
  }

  private static String formatRow(List<String> cells, int[] widths) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < widths.length; i++) {
      if (i > 0) {
        sb.append("  ");
      }
      String value = cell(cells, i);
      sb.append(value);
      // Pad all but the last column.
      if (i < widths.length - 1) {
        sb.append(" ".repeat(widths[i] - value.length()));
      }
    }
    return sb.toString();
  }

  private static String cell(List<String> cells, int i) {
    return i < cells.size() && cells.get(i) != null ? cells.get(i) : "";
  }

  /** Prints a single informational line (respecting nothing fancy; used for show/confirmations). */
  public void line(String text) {
    out.println(text);
  }
}
