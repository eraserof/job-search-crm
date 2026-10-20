# Anthropic Messages API — Reference Notes

Behavioral reference for the Anthropic Messages API, captured for the `AnthropicLlmClient`
adapter. This describes **how the API behaves**, not how we map it — the mapping lives in the code.

Sources: Anthropic Messages API documentation and the AWS Bedrock Anthropic Claude reference.
Content rephrased for compliance with licensing restrictions. Verify against the official docs
before relying on edge-case details, as the API evolves.

---

## Endpoint

```
POST https://api.anthropic.com/v1/messages
```

---

## Request Headers

| Header | Value | Notes |
|--------|-------|-------|
| `x-api-key` | the account API key | Authentication. Secret — must never be logged. |
| `anthropic-version` | `2023-06-01` | A **dated API contract**, not a model version. Stable; unrelated to which Claude model is used. |
| `content-type` | `application/json` | |

Beta features are opted into with an `anthropic-beta` header carrying one or more dated feature
flags (e.g. an effort-control beta). Not needed for ordinary completions.

---

## Request Body

Top-level fields:

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `model` | string | yes | e.g. `claude-sonnet-4-5`. |
| `max_tokens` | integer | yes | Maximum tokens to generate. The API **requires** this on every request. |
| `messages` | array | yes | The conversation turns (see below). First message must be `user`. |
| `system` | string | no | The system prompt. **Top-level field, not a message** — there is no `system` role inside `messages`. |
| `temperature` | number | no | Sampling randomness. |
| `top_p` | number | no | Nucleus sampling. |
| `top_k` | integer | no | Top-K sampling. |
| `tools` | array | no | Tool definitions the model may call (see below). |

### `temperature` vs `top_p` on 4.5 models

Claude Sonnet 4.5 and Claude Haiku 4.5 accept **either `temperature` or `top_p`, but not both**
in the same request. Sending both to a 4.5 model is an error. Older models do not have this
restriction.

### The `messages` array

Each entry is an object with a `role` and `content`:

- Allowed roles inside `messages`: **`user` and `assistant` only.** (The system prompt is the
  separate top-level `system` field; there is no `system` entry here.)
- The first message must use the `user` role.
- `content` may be either:
  - a plain string (simple text turn), or
  - an array of typed **content blocks** (for mixed/structured content such as tool results).

### Tool definitions (`tools`)

Each tool is an object:

| Field | Type | Notes |
|-------|------|-------|
| `name` | string | Tool identifier the model will reference when calling it. |
| `description` | string | Natural-language description guiding when to use the tool. |
| `input_schema` | object | A **JSON Schema object** (embedded JSON, not a string) describing the tool's arguments. |

---

## Response Body

A successful (HTTP 200) response is an object shaped like:

```json
{
  "id": "msg_...",
  "type": "message",
  "role": "assistant",
  "model": "claude-sonnet-4-5",
  "content": [ /* array of content blocks */ ],
  "stop_reason": "end_turn",
  "stop_sequence": null,
  "usage": { "input_tokens": 1234, "output_tokens": 87 }
}
```

### `content` is an array of typed blocks

The assistant's reply is **not** a single string — it is a list of blocks, each tagged with a
`type`. The two types relevant to ordinary + tool-use completions:

- **`text`** block:
  ```json
  { "type": "text", "text": "Let me look that up." }
  ```
- **`tool_use`** block (the model is requesting a tool call):
  ```json
  {
    "type": "tool_use",
    "id": "toolu_01A...",
    "name": "searchOpportunities",
    "input": { "query": "Acme" }
  }
  ```
  - `id` — unique identifier for this specific call; used to correlate the eventual result.
  - `name` — the tool being invoked.
  - `input` — a JSON object holding the arguments the model chose.

A single response may contain **multiple** blocks — e.g. a leading `text` block of reasoning
followed by one or more `tool_use` blocks. It may also request **several** `tool_use` blocks in
one response.

### `stop_reason`

Present on every successful response; indicates why generation stopped:

| Value | Meaning |
|-------|---------|
| `end_turn` | The model finished its reply naturally (a final answer). |
| `tool_use` | The model paused to request one or more tool calls; `content` contains `tool_use` block(s). |
| `max_tokens` | Generation hit the `max_tokens` cap and was truncated. |
| `stop_sequence` | A configured stop sequence was produced. |
| `refusal` | The model declined to continue. |
| `pause_turn` | A long-running turn was paused (used by certain server-side tools). |

The practical distinction: `tool_use` means "the caller must run tools and continue the
conversation"; everything else is a terminal reply for this exchange.

### `usage`

Token accounting for the request:

```json
"usage": { "input_tokens": 1234, "output_tokens": 87 }
```

---

## Tool Use — Two-Turn Pattern

Tool use is a **round-trip** between caller and model, not a single call:

1. The caller sends a request including `tools`.
2. If the model wants a tool, it responds with `stop_reason: "tool_use"` and one or more
   `tool_use` blocks in `content`.
3. The caller executes the tool(s) and continues the conversation by appending:
   - the assistant message that contained the `tool_use` block(s), then
   - a new **`user`-role** message whose `content` is an array containing one `tool_result`
     block per call. A `tool_result` block references the originating call:
     ```json
     {
       "type": "tool_result",
       "tool_use_id": "toolu_01A...",
       "content": "…result payload…"
     }
     ```
     (`tool_use_id` must match the `id` from the model's `tool_use` block. An `is_error: true`
     flag may be set to signal the tool failed.)
4. The caller sends the extended conversation back; the model either requests more tools or
   returns a final `end_turn` reply.

Key point: **tool results are delivered as `user`-role messages carrying `tool_result` content
blocks** — there is no dedicated "tool" role in the wire format.

---

## Errors

Errors are returned with a non-2xx HTTP status and a JSON body:

```json
{
  "type": "error",
  "error": {
    "type": "invalid_request_error",
    "message": "…human-readable description…"
  }
}
```

Status codes observed / documented:

| Status | Meaning |
|--------|---------|
| `400` | Invalid request (malformed body, invalid parameter combination such as `temperature` + `top_p` on a 4.5 model). |
| `401` | Authentication failure (missing/invalid `x-api-key`). |
| `403` | Permission denied for the requested resource. |
| `404` | Resource not found. |
| `413` | Request body too large. |
| `429` | Rate limited. |
| `500` | Internal server error on Anthropic's side. |
| `529` | Service temporarily overloaded. |

The `error.type` string (e.g. `invalid_request_error`, `authentication_error`,
`rate_limit_error`, `overloaded_error`) gives the machine-readable category.

---

## Notes / Caveats

- The documentation site renders content client-side, so exact field-by-field schemas are best
  confirmed against the live API responses or the official reference rather than scraped pages.
- Streaming (`stream: true` with `InvokeModelWithResponseStream`-style SSE) is a separate response
  mode not covered here; these notes describe the non-streaming request/response only.
- Model IDs and the `temperature`/`top_p` exclusivity rule are model-generation specific; re-verify
  when targeting a different Claude generation.
