package com.jobcrm.codegen.java;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class MethodLocatorTest {

  private static final String SOURCE =
      """
      package x;

      import java.util.List;

      class Repo implements Store {
        @Override
        public List<String> find(String key) {
          String s = "} not a brace {";
          return List.of(s);
        }

        public List<String> find(String key, int limit) {
          return List.of();
        }

        static String text() {
          return \"""
              { "json": "}" }
              \""";
        }
      }

      interface Store {
        List<String> find(String key);
      }
      """;

  @Test
  void findsTheBodyByNameAndParameterTypes() {
    Optional<MethodLocator.BodyRange> range =
        MethodLocator.find(SOURCE, "public List<String> find(String key)");

    assertThat(range).isPresent();
    String body = SOURCE.substring(range.get().open(), range.get().close() + 1);
    assertThat(body).startsWith("{").endsWith("}").contains("not a brace").doesNotContain("limit");
    assertThat(range.get().indent()).isEqualTo("  ");
  }

  @Test
  void tellsOverloadsApartAndIgnoresInterfaceDeclarations() {
    assertThat(MethodLocator.count(SOURCE, "List<String> find(String key)")).isEqualTo(1);
    assertThat(MethodLocator.count(SOURCE, "List<String> find(String k, int n)")).isEqualTo(1);
    assertThat(MethodLocator.count(SOURCE, "void find()")).isZero();
  }

  @Test
  void ignoresModifiersReturnTypeParameterNamesAndPackageQualifiers() {
    assertThat(MethodLocator.find(SOURCE, "private Object find(java.lang.String other)")).isPresent();
  }

  @Test
  void handlesTextBlocksWithBraces() {
    Optional<MethodLocator.BodyRange> range = MethodLocator.find(SOURCE, "static String text()");

    assertThat(range).isPresent();
    assertThat(SOURCE.substring(range.get().open(), range.get().close() + 1)).contains("\"json\"");
  }

  @Test
  void ambiguousMatchesAreNotFound() {
    String twice =
        """
        class A {
          void run() {}
          static class B {
            void run() {}
          }
        }
        """;

    assertThat(MethodLocator.count(twice, "void run()")).isEqualTo(2);
    assertThat(MethodLocator.find(twice, "void run()")).isEmpty();
  }

  @Test
  void worksWithWindowsLineEndings() {
    String crlf = "class A {\r\n  int one() {\r\n    return 1;\r\n  }\r\n}\r\n";

    Optional<MethodLocator.BodyRange> range = MethodLocator.find(crlf, "int one()");

    assertThat(range).isPresent();
    assertThat(crlf.charAt(range.get().open())).isEqualTo('{');
    assertThat(crlf.charAt(range.get().close())).isEqualTo('}');
    assertThat(crlf.substring(range.get().open(), range.get().close() + 1)).contains("return 1;");
  }

  @Test
  void unparsableSourceCountsAsMinusOne() {
    assertThat(MethodLocator.count("class A { void f( }", "void f()")).isEqualTo(-1);
    assertThat(MethodLocator.find("class A { void f( }", "void f()")).isEmpty();
  }

  @Test
  void rejectsSomethingThatIsNotASignature() {
    assertThatThrownBy(() -> MethodLocator.count(SOURCE, "this is not java"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not a method signature");
  }
}
