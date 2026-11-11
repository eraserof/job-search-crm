package com.jobcrm.codegen.scaffold;

import com.jobcrm.codegen.scaffold.ScaffoldReply.AddTo;
import com.jobcrm.codegen.scaffold.ScaffoldReply.NewFile;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits a scaffold reply into FILE, ADD TO and MANUAL blocks. Text outside blocks is ignored. A
 * marker line inside an open block is block content, not a new block.
 */
public final class ScaffoldParser {

  private static final Pattern FILE_OPEN = Pattern.compile("^=== FILE: (.+?) ===\\s*$");
  private static final Pattern ADD_OPEN = Pattern.compile("^=== ADD TO: (.+?) :: (\\S+) ===\\s*$");
  private static final Pattern MANUAL_OPEN = Pattern.compile("^=== MANUAL ===\\s*$");
  private static final String FILE_END = "=== END FILE ===";
  private static final String ADD_END = "=== END ADD ===";
  private static final String MANUAL_END = "=== END MANUAL ===";

  private ScaffoldParser() {}

  /** Never throws: structural problems are returned in {@link ScaffoldReply#problems()}. */
  public static ScaffoldReply parse(String reply) {
    List<NewFile> files = new ArrayList<>();
    List<AddTo> additions = new ArrayList<>();
    List<String> manual = new ArrayList<>();
    List<String> problems = new ArrayList<>();

    String[] lines = (reply == null ? "" : reply).split("\\R", -1);
    int i = 0;
    while (i < lines.length) {
      String line = lines[i];
      Matcher file = FILE_OPEN.matcher(line);
      Matcher add = ADD_OPEN.matcher(line);
      if (file.matches()) {
        Block block = readUntil(lines, i + 1, FILE_END);
        if (block.closed) {
          files.add(new NewFile(file.group(1).trim(), block.text));
        } else {
          problems.add(
              "FILE block for " + file.group(1).trim() + " never closed (truncated reply?)");
        }
        i = block.next;
      } else if (add.matches()) {
        Block block = readUntil(lines, i + 1, ADD_END);
        if (block.closed) {
          additions.add(new AddTo(add.group(1).trim(), add.group(2), block.text));
        } else {
          problems.add(
              "ADD TO block for " + add.group(1).trim() + " never closed (truncated reply?)");
        }
        i = block.next;
      } else if (MANUAL_OPEN.matcher(line).matches()) {
        Block block = readUntil(lines, i + 1, MANUAL_END);
        if (block.closed) {
          manual.addAll(manualItems(block.text));
        } else {
          problems.add("MANUAL block never closed (truncated reply?)");
        }
        i = block.next;
      } else {
        i++;
      }
    }
    return new ScaffoldReply(files, additions, manual, problems);
  }

  /** Lines from {@code start} up to the line equal to {@code endMarker}, joined with "\n". */
  private static Block readUntil(String[] lines, int start, String endMarker) {
    StringBuilder text = new StringBuilder();
    for (int i = start; i < lines.length; i++) {
      if (lines[i].stripTrailing().equals(endMarker)) {
        return new Block(text.toString(), true, i + 1);
      }
      text.append(lines[i]).append('\n');
    }
    return new Block(text.toString(), false, lines.length);
  }

  /** One entry per non-blank line, with a leading "- " removed. */
  private static List<String> manualItems(String text) {
    List<String> items = new ArrayList<>();
    for (String line : text.split("\\R")) {
      String item = line.strip();
      if (item.startsWith("- ")) {
        item = item.substring(2).strip();
      }
      if (!item.isEmpty()) {
        items.add(item);
      }
    }
    return items;
  }

  private record Block(String text, boolean closed, int next) {}
}
