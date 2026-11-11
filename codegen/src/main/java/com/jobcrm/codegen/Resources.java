package com.jobcrm.codegen;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Prompt templates and schemas packaged under {@code src/main/resources/com/jobcrm/codegen/}. */
public final class Resources {

  private Resources() {}

  public static String text(String name) {
    try (InputStream in = Resources.class.getResourceAsStream(name)) {
      if (in == null) {
        throw new IllegalStateException("missing resource com/jobcrm/codegen/" + name);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("cannot read resource " + name, e);
    }
  }
}
