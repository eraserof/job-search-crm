package com.jobcrm.codegen.config;

/**
 * One entry under {@code models.json} "stages" ("plan", "code", "repair"). Boxed fields are
 * optional in the JSON and may be null; {@link ModelsConfig#resolve(String)} applies defaults.
 *
 * @param platform key into "platforms"
 * @param model model id on that platform (as listed by GET /models)
 * @param temperature nullable; default 0.2
 * @param timeoutSec legacy total timeout used by the PowerShell scripts; ignored by the Java tool
 * @param firstTokenTimeoutSec nullable; max wait for the first streamed data line, which covers
 *     queueing behind other requests and model loading; default 900
 * @param idleTimeoutSec nullable; max gap between streamed lines once data has started; default 120
 */
public record StageSpec(
    String platform,
    String model,
    Double temperature,
    Integer timeoutSec,
    Integer firstTokenTimeoutSec,
    Integer idleTimeoutSec) {}
