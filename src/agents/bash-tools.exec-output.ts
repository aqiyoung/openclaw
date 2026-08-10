/**
 * Rendering helpers for exec output/status updates.
 * Keeps no-output placeholders and warning placement consistent across exec
 * progress, polling, and completion surfaces.
 */
import type { TerminationReason } from "../process/supervisor/types.js";

const EXEC_NO_OUTPUT_PLACEHOLDER = "（无输出）";
const EXEC_TIMEOUT_RETRY_GUIDANCE =
  "命令被终止，但外部副作用可能已经执行完毕。请先验证结果状态再重试。不要自动重新运行非幂等命令。仅在确认命令安全时才使用更高的超时时间。";

/** Render command output with a stable placeholder for empty output. */
export function renderExecOutputText(value: string | undefined): string {
  return value || EXEC_NO_OUTPUT_PLACEHOLDER;
}

/** Render the authoritative process exit without inventing a successful code. */
export function renderExecExitLabel(exit: {
  exitCode?: number | null;
  exitSignal?: NodeJS.Signals | number | null;
}): string {
  if (exit.exitSignal != null) {
    return `signal ${exit.exitSignal}`;
  }
  return typeof exit.exitCode === "number" ? `code ${exit.exitCode}` : "unknown exit code";
}

/** Render the text shown in exec progress updates, including warnings first. */
export function renderExecUpdateText(params: { tailText?: string; warnings: string[] }): string {
  const warningText = params.warnings.length ? `${params.warnings.join("\n")}\n\n` : "";
  return warningText + renderExecOutputText(params.tailText);
}

/** Add retry-safety guidance only for supervisor timeout exits. */
export function appendExecTimeoutRetryGuidance(
  text: string,
  exitReason: TerminationReason | undefined,
): string {
  if (exitReason !== "overall-timeout" && exitReason !== "no-output-timeout") {
    return text;
  }
  return `${text}\n\n${EXEC_TIMEOUT_RETRY_GUIDANCE}`;
}
