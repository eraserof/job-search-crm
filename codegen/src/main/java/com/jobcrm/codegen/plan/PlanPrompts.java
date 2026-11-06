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
   * -----\n" + repo.read(repo.resolve(path)) + "\n"}. Concatenate them all; an empty list gives "".
   */
  public static String contextBlock(Repo repo, List<String> paths) {
    StringBuilder sb = new StringBuilder();
    for (String path : paths) {
      sb.append("\n----- FILE: ").append(path).append("\n-----\n");
      sb.append(repo.read(repo.resolve(path))).append("\n");
    }
    return sb.toString();
  }

  /**
   * The planner prompt: {@code template} with {@code "{{ASK}}"} replaced by {@code ask} and {@code
   * "{{CONTEXT}}"} replaced by {@code context}, using literal {@code String.replace}, ASK first.
   */
  public static String planner(String template, String ask, String context) {
    return template.replace("{{ASK}}", ask).replace("{{CONTEXT}}", context);
  }

  /**
   * The re-ask after a rejected plan. Returns {@code basePrompt + "\n\n=== Your previous plan was
   * rejected ===\n" + previousJson + "\n\n=== Validation errors ===\n" + String.join("\n", errors)
   * + "\n\nReturn a corrected JSON plan only."}. A null previousJson is written as "(none)".
   */
  public static String retry(String basePrompt, String previousJson, List<String> errors) {
    String previous = previousJson == null ? "(none)" : previousJson;
    return basePrompt
        + "\n\n=== Your previous plan was rejected ===\n"
        + previous
        + "\n\n=== Validation errors ===\n"
        + String.join("\n", errors)
        + "\n\nReturn a corrected JSON plan only.";
  }

  /**
   * The JSON object inside a reply that may contain {@code <think>} blocks or markdown fences.
   * Remove every {@code <think>...</think>} block with {@code THINK.matcher(raw).replaceAll("")}
   * (the constant below), then take the text from the first '{' to the last '}' inclusive. Empty if
   * there is no '{', or the last '}' comes before it.
   */
  public static Optional<String> extractJson(String raw) {
    String cleaned = PlanPrompts.THINK.matcher(raw).replaceAll("");
    int start = cleaned.indexOf('{');
    if (start == -1) {
      return Optional.empty();
    }
    int end = cleaned.lastIndexOf('}');
    if (end < start) {
      return Optional.empty();
    }
    return Optional.of(cleaned.substring(start, end + 1));
  }

  /**
   * The OpenAI {@code response_format} that makes LM Studio enforce the plan shape: {@code {"type":
   * "json_schema", "json_schema": {"name": "plan", "strict": true, "schema": <schema>}}}, built
   * with {@code json.createObjectNode()}, where {@code schema} is passed in (the parsed
   * plan-schema.json resource).
   */
  public static ObjectNode responseFormat(ObjectMapper json, JsonNode schema) {
    ObjectNode root = json.createObjectNode();
    root.put("type", "json_schema");
    ObjectNode spec = root.putObject("json_schema");
    spec.put("name", "plan");
    spec.put("strict", true);
    spec.set("schema", schema);
    return root;
  }

  /**
   * The house rules from {@code codegen/TEMPLATE.txt}: everything before the first line starting
   * with "=== The class", with trailing whitespace removed. If that marker is missing, the whole
   * template with trailing whitespace removed.
   */
  public static String rulesHeader(String template) {
    int idx = template.indexOf("\n=== The class");
    if (idx == -1 && template.startsWith("=== The class")) {
      return "".stripTrailing();
    }
    if (idx == -1) {
      return template.stripTrailing();
    }
    return template.substring(0, idx).stripTrailing();
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
    StringBuilder sb = new StringBuilder();
    sb.append(rules).append("\n\n");
    sb.append("=== The class this method belongs to (relevant fields) ===\n");
    sb.append(job.classContext()).append("\n\n");
    sb.append("=== Types and methods you may use ===\n");
    sb.append(job.types()).append("\n\n");
    sb.append("=== Contract ===\n");
    sb.append("Implement:  ").append(job.signature()).append("\n\n");
    for (String step : job.behavior()) {
      sb.append("- ").append(step).append("\n");
    }
    return sb.toString();
  }

  // The DOTALL think-block pattern used by extractJson.
  static final Pattern THINK = Pattern.compile("<think>.*?</think>", Pattern.DOTALL);
}
