package com.jobcrm.codegen.scaffold;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobcrm.codegen.Repo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScaffoldValidatorTest {

  @TempDir Path root;

  @Test
  void existingTargetsListsOnlyFilesAlreadyInTheRepo() throws IOException {
    Files.createDirectories(root.resolve("a"));
    Files.writeString(root.resolve("a/Old.java"), "package a;\nfinal class Old {}\n");
    ScaffoldReply reply =
        new ScaffoldReply(
            List.of(
                new ScaffoldReply.NewFile("a/Old.java", "package a;\nfinal class Old {}\n"),
                new ScaffoldReply.NewFile("a/New.java", "package a;\nfinal class New {}\n")),
            List.of(),
            List.of(),
            List.of());

    assertThat(ScaffoldValidator.existingTargets(reply, new Repo(root))).containsExactly("a/Old.java");
  }

  @Test
  void validateRejectsExistingFileTruncationAndWrongPackage() throws IOException {
    Files.createDirectories(root.resolve("src/main/java/a"));
    Files.writeString(root.resolve("src/main/java/a/Old.java"), "package a;\nfinal class Old {}\n");
    String reply =
        """
        === FILE: src/main/java/a/Old.java ===
        package a;
        final class Old {}
        === END FILE ===
        === FILE: src/main/java/a/Wrong.java ===
        package b;
        final class Wrong {}
        === END FILE ===
        === FILE: src/main/java/a/Cut.java ===
        package a;
        """;

    List<String> errors =
        ScaffoldValidator.validate(ScaffoldParser.parse(reply), new Repo(root));

    assertThat(errors)
        .anyMatch(e -> e.contains("Old.java") && e.contains("already exists"))
        .anyMatch(e -> e.contains("Wrong.java") && e.contains("package is b"))
        .anyMatch(e -> e.contains("Cut.java") && e.contains("never closed"));
  }

  @Test
  void validateRejectsWrongStubMessageAndWildcardImport() {
    String reply =
        """
        === FILE: src/main/java/a/S.java ===
        package a;
        import java.util.*;
        final class S {
          void f() { throw new UnsupportedOperationException("later"); }
        }
        === END FILE ===
        """;

    List<String> errors =
        ScaffoldValidator.validate(ScaffoldParser.parse(reply), new Repo(root));

    assertThat(errors)
        .anyMatch(e -> e.contains("wildcard import"))
        .anyMatch(e -> e.contains("TODO(codegen)"));
  }
}
