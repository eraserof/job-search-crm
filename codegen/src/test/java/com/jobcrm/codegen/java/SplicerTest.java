package com.jobcrm.codegen.java;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class SplicerTest {

  @Test
  void replacesOnlyTheBodyAndAlignsTheClosingBrace() {
    String source =
        """
        package x;

        import java.util.List;

        class A {
          List<String> names() {
            return null;
          }

          int other() {
            return 2;
          }
        }
        """;

    String out =
        Splicer.splice(source, "List<String> names()", "    return List.of(\"a\");", List.of());

    assertThat(out)
        .isEqualTo(
            """
            package x;

            import java.util.List;

            class A {
              List<String> names() {
                return List.of("a");
              }

              int other() {
                return 2;
              }
            }
            """);
  }

  @Test
  void keepsWindowsLineEndings() {
    String source = "class A {\r\n  int one() {\r\n    return 0;\r\n  }\r\n}\r\n";

    String out =
        Splicer.splice(source, "int one()", "\n    int x = 1;\n    return x;\n", List.of());

    assertThat(out)
        .isEqualTo("class A {\r\n  int one() {\r\n    int x = 1;\r\n    return x;\r\n  }\r\n}\r\n");
  }

  @Test
  void addsMissingImportsAfterTheLastImportAndSkipsExistingOnes() {
    String source =
        """
        package x;

        import java.util.List;

        class A {
          void f() {}
        }
        """;

    String out =
        Splicer.splice(source, "void f()", "", List.of("java.util.List", "java.util.UUID"));

    assertThat(out).contains("import java.util.List;\nimport java.util.UUID;\n\nclass A");
    assertThat(out.split("import java.util.List;", -1)).hasSize(2);
  }

  @Test
  void addsTheFirstImportAfterThePackageLine() {
    String source = "package x;\n\nclass A {\n  void f() {}\n}\n";

    String out = Splicer.splice(source, "void f()", "", List.of("java.util.UUID"));

    assertThat(out).startsWith("package x;\nimport java.util.UUID;\n");
  }

  @Test
  void refusesWhenTheMethodIsNotFound() {
    assertThatThrownBy(() -> Splicer.splice("class A {}", "void f()", "", List.of()))
        .isInstanceOf(SpliceException.class)
        .hasMessageContaining("not found exactly once");
  }
}
