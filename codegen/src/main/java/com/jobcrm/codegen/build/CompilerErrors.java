package com.jobcrm.codegen.build;

/** Pulls the javac errors for one file out of Gradle's output, for the retry and repair prompts. */
public final class CompilerErrors {

  private CompilerErrors() {}

  /**
   * javac error blocks for {@code fileName} (e.g. "JdbcOpportunityRepository.java"), deduplicated
   * and joined with "\n". Gradle prints each error twice (the second copy indented, in its failure
   * summary), so blocks are compared after stripping each line.
   *
   * <ol>
   *   <li>Split {@code output} into lines ({@code output.split("\\R")}).
   *   <li>A block starts at a line matching {@code Pattern.quote(fileName) + ":\\d+: error:"} (use
   *       {@code find()}, not {@code matches()}).
   *   <li>The block is that line plus up to the next 4 lines, stopping early at a line that matches
   *       {@code ":\\d+: (error|warning):"} or {@code "^\\s*\\d+ errors?$"}. Strip every line in
   *       the block.
   *   <li>Join the block's lines with "\n" and add it to a {@code LinkedHashSet<String>}, which
   *       drops the duplicate copy and keeps the order.
   *   <li>Return the set joined with "\n". If it is empty, return {@code output} unchanged so the
   *       model still sees something.
   * </ol>
   */
  public static String forFile(String output, String fileName) {
    java.util.regex.Pattern startPattern =
        java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(fileName) + ":\\d+: error:");
    java.util.regex.Pattern stopErrorWarningPattern =
        java.util.regex.Pattern.compile(":\\d+: (error|warning):");
    java.util.regex.Pattern stopErrorsPattern =
        java.util.regex.Pattern.compile("^\\s*\\d+ errors?$");

    String[] lines = output.split("\\R");
    java.util.LinkedHashSet<String> blocks = new java.util.LinkedHashSet<>();

    for (int i = 0; i < lines.length; i++) {
      if (!startPattern.matcher(lines[i]).find()) continue;

      int end = Math.min(lines.length, i + 5);
      java.util.ArrayList<String> blockLines = new java.util.ArrayList<>();
      for (int j = i; j < end; j++) {
        if (j > i
            && (stopErrorWarningPattern.matcher(lines[j]).find()
                || stopErrorsPattern.matcher(lines[j]).find())) break;
        blockLines.add(lines[j].strip());
      }
      blocks.add(String.join("\n", blockLines));
    }

    return blocks.isEmpty() ? output : String.join("\n", blocks);
  }
}
