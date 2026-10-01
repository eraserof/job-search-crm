package com.jobcrm.infrastructure.persistence;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

/**
 * Conversions between SQLite's TEXT storage form and the domain's Java types. UUIDs are stored as
 * their canonical string; {@link Instant}s as ISO-8601 strings (the default {@code
 * Instant#toString} / {@code Instant#parse} round-trip).
 */
public final class SqlTypes {

  private SqlTypes() {}

  public static String uuid(UUID value) {
    return value.toString();
  }

  public static UUID uuid(String value) {
    return UUID.fromString(value);
  }

  public static String instant(Instant value) {
    return value.toString();
  }

  public static Instant instant(String value) {
    return Instant.parse(value);
  }

  /** Reads a column that may be SQL NULL, returning {@code null} rather than empty string. */
  public static String nullableString(ResultSet rs, String column) throws SQLException {
    String v = rs.getString(column);
    return rs.wasNull() ? null : v;
  }

  /** Reads a nullable UUID column, returning {@code null} when absent. */
  public static UUID nullableUuid(ResultSet rs, String column) throws SQLException {
    String v = rs.getString(column);
    return (v == null || rs.wasNull()) ? null : UUID.fromString(v);
  }

  /** Reads a nullable ISO-8601 instant column, returning {@code null} when absent. */
  public static Instant nullableInstant(ResultSet rs, String column) throws SQLException {
    String v = rs.getString(column);
    return (v == null || rs.wasNull()) ? null : Instant.parse(v);
  }

  /** Binds a possibly-null string, using {@code setNull} when absent. */
  public static void setNullableString(PreparedStatement ps, int index, String value)
      throws SQLException {
    if (value == null) {
      ps.setNull(index, java.sql.Types.VARCHAR);
    } else {
      ps.setString(index, value);
    }
  }
}
