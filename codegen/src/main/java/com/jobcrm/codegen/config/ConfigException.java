package com.jobcrm.codegen.config;

/** {@code models.json} is missing, unreadable, or references something that does not exist. */
public final class ConfigException extends RuntimeException {
  public ConfigException(String message) {
    super(message);
  }

  public ConfigException(String message, Throwable cause) {
    super(message, cause);
  }
}
