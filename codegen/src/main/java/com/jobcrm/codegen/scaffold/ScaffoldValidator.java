package com.jobcrm.codegen.scaffold;

import com.jobcrm.codegen.Repo;
import com.jobcrm.codegen.scaffold.ScaffoldReply.AddTo;
import com.jobcrm.codegen.scaffold.ScaffoldReply.NewFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Checks a parsed scaffold reply against the real repo before anything is written. */
public final class ScaffoldValidator {

  private static final Pattern PACKAGE = Pattern.compile("(?m)^package\\s+([\\w.]+)\\s*;");
  private static final Pattern WILDCARD_IMPORT = Pattern.compile("(?m)^import\\s+[\\w.]+\\.\\*;");
  private static final Pattern UNSUPPORTED =
      Pattern.compile("UnsupportedOperationException\\(\\s*\"([^\"]*)\"\\s*\\)");
  private static final String STUB_MESSAGE = "TODO(codegen)";
  private static final String JAVA_DIR = "/java/";

  private ScaffoldValidator() {}

  /** All problems found; empty means the reply may be saved for review. */
  public static List<String> validate(ScaffoldReply reply, Repo repo) {
    List<String> errors = new ArrayList<>(reply.problems());
    if (reply.files().isEmpty() && reply.additions().isEmpty()) {
      errors.add("reply contained no FILE or ADD TO blocks");
    }

    Set<String> seen = new HashSet<>();
    for (NewFile file : reply.files()) {
      String where = "FILE " + file.path();
      if (!seen.add(file.path())) {
        errors.add(where + ": listed twice");
      }
      if (checkPath(file.path(), repo, where, errors)) {
        if (Files.exists(repo.resolve(file.path()))) {
          errors.add(where + ": already exists (use ADD TO, or leave it alone)");
        }
        checkPackage(file, errors);
        checkSource(file.source(), where, errors);
      }
    }
    for (AddTo add : reply.additions()) {
      String where = "ADD TO " + add.path() + " :: " + add.className();
      if (checkPath(add.path(), repo, where, errors)) {
        Path target = repo.resolve(add.path());
        if (!Files.isRegularFile(target)) {
          errors.add(where + ": file does not exist (use FILE for a new file)");
        } else if (!declaresType(repo.read(target), add.className())) {
          errors.add(where + ": no type named " + add.className() + " in that file");
        }
        checkSource(add.body(), where, errors);
      }
    }
    return errors;
  }

  /**
   * Paths of FILE blocks whose target already exists. Re-asking cannot fix this (the ask itself
   * collides with the repo), so the caller stops instead of retrying.
   */
  public static List<String> existingTargets(ScaffoldReply reply, Repo repo) {
    List<String> existing = new ArrayList<>();
    for (NewFile file : reply.files()) {
      if (checkPath(file.path(), repo, "", new ArrayList<>())
          && Files.exists(repo.resolve(file.path()))) {
        existing.add(file.path());
      }
    }
    return existing;
  }

  /** True if the path is a usable repo-relative .java path; otherwise adds the reason. */
  private static boolean checkPath(String path, Repo repo, String where, List<String> errors) {
    if (path.isBlank() || !path.endsWith(".java")) {
      errors.add(where + ": path must end in .java");
      return false;
    }
    if (Path.of(path).isAbsolute()) {
      errors.add(where + ": path must be repo-relative");
      return false;
    }
    if (!repo.resolve(path).startsWith(repo.root())) {
      errors.add(where + ": path leaves the repo");
      return false;
    }
    return true;
  }

  /** The directory under {@code .../java/} must equal the package declaration. */
  private static void checkPackage(NewFile file, List<String> errors) {
    String path = file.path().replace('\\', '/');
    int idx = path.lastIndexOf(JAVA_DIR);
    if (idx < 0) {
      return;
    }
    String dir = path.substring(idx + JAVA_DIR.length(), Math.max(idx + JAVA_DIR.length(), path.lastIndexOf('/')));
    String expected = dir.replace('/', '.');
    Matcher m = PACKAGE.matcher(file.source());
    if (!m.find()) {
      errors.add("FILE " + file.path() + ": no package declaration");
    } else if (!m.group(1).equals(expected)) {
      errors.add(
          "FILE " + file.path() + ": package is " + m.group(1) + " but the directory says " + expected);
    }
  }

  private static void checkSource(String source, String where, List<String> errors) {
    if (WILDCARD_IMPORT.matcher(source).find()) {
      errors.add(where + ": wildcard import");
    }
    Matcher m = UNSUPPORTED.matcher(source);
    while (m.find()) {
      if (!m.group(1).equals(STUB_MESSAGE)) {
        errors.add(where + ": stub message must be exactly \"" + STUB_MESSAGE + "\", got \"" + m.group(1) + "\"");
      }
    }
  }

  private static boolean declaresType(String source, String className) {
    return Pattern.compile("\\b(class|record|interface|enum)\\s+" + Pattern.quote(className) + "\\b")
        .matcher(source)
        .find();
  }

  /** How many stub bodies a source text contains. */
  public static int countStubs(String source) {
    Matcher m = UNSUPPORTED.matcher(source);
    int n = 0;
    while (m.find()) {
      if (m.group(1).equals(STUB_MESSAGE)) {
        n++;
      }
    }
    return n;
  }
}
