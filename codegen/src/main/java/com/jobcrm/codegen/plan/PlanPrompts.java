package com.jobcrm.codegen.plan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jobcrm.codegen.Repo;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/** Text building and parsing for the plan stage. Templates live in resources. */
public final class PlanPrompts {

  private PlanPrompts() {}

  /**
   * Every context file inlined, in order. For each path: {@code "\n----- FILE: " + path + "
   * -----\n" + repo.read(repo.resolve(path)) + "\n"}. Concatenate them all; an empty list gives
   * "".
   */
  public static String contextBlock(Repo repo, List<String> paths) {
    throw new UnsupportedOperationException("TODO(codegen)");
  }

  /**
   * The planner prompt: {@code template} with {@code "{{ASK}}"} replaced by {@code ask} and
   * {@code "{{CONTEXT}}"} replaced by {@code context}, using literal {@code String.replace},
   * ASK first.
   */
  public static String planner(String template, String ask, String context) {
    throw new UnsupportedOperationException("TODO(codegen)");
  }

  /**
   * The re-ask after a rejected plan. Returns {@code basePrompt + "\n\n=== Your previous plan was
   * rejected ===\n" + previousJson + "\n\n=== Validation errors ===\n" + String.join("\n",
   * errors) + "\n\nReturn a corrected JSON plan only."}. A null previousJson is written as
   * "(none)".
   */
  public static String retry(String basePrompt, String previousJson, List<String> errors) {
    throw new UnsupportedOperationException("TODO(codegen)");
  }

  /**
   * The JSON object inside a reply that may contain {@code <think>} blocks or markdown fences.
   * Remove every {@code <think>...</think>} block with {@code THINK.matcher(raw).replaceAll("")}
   * (the constant below), then take the text from
   * the first '{' to the last '}' inclusive. Empty if there is no '{', or the last '}' comes
   * before it.
   */
  public static Optional<String> extractJson(String raw) {
    throw new UnsupportedOperationException("TODO(codegen)");
  }

  /**
   * The OpenAI {@code response_format} that makes LM Studio enforce the plan shape: {@code
   * {"type": "json_schema", "json_schema": {"name": "plan", "strict": true, "schema": <schema>}}},
   * built with {@code json.createObjectNode()}, where {@code schema} is passed in (the parsed
   * plan-schema.json resource).
   */
  public static ObjectNode responseFormat(ObjectMapper json, JsonNode schema) {
    throw new UnsupportedOperationException("TODO(codegen)");
  }

  /**
   * The house rules from {@code codegen/TEMPLATE.txt}: everything before the first line starting
   * with "=== The class", with trailing whitespace removed. If that marker is missing, the whole
   * template with trailing whitespace removed.
   */
  public static String rulesHeader(String template) {
    throw new UnsupportedOperationException("TODO(codegen)");
  }

  /**
   * The code-stage prompt for one job. Returns, with "\n" line breaks:
   *
   * <pre>
   * rules
   * (blank line)
   * === The class this method belongs to (relevant fields) ===
   * job.classContext()
   * (blank line)
   * === Types and methods you may use ===
   * job.types()
   * (blank line)
   * === Contract ===
   * Implement:  job.signature()        (two spaces after the colon)
   * (blank line)
   * Behavior:
   * - step 1
   * - step 2 ...                       (one "- " line per job.behavior() entry)
   * </pre>
   */
  public static String codePrompt(String rules, PlanJob job) {
    throw new UnsupportedOperationException("TODO(codegen)");
  }

  // The DOTALL think-block pattern used by extractJson.
  static final Pattern THINK = Pattern.compile("<think>.*?</think>", Pattern.DOTALL);
}
