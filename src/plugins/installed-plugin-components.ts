import type { PluginInstalledComponents } from "../../packages/gateway-protocol/src/schema/plugins.js";

/**
 * Component set for a plugin whose runtime surface has not been mapped.
 *
 * Upstream projects the installed artifact (skill resolution plus bundle MCP/LSP
 * inspection) to report which components the runtime can actually use. That
 * inspection is not ported here yet, so callers report the empty set instead of
 * claiming that declared capabilities are usable at runtime; clients still get
 * the declared surface, which `plugins.inspect` already returns.
 */
export function emptyInstalledPluginComponents(): PluginInstalledComponents {
  return {
    mapped: [],
    skills: [],
    mcpServers: [],
    commands: [],
    hooks: [],
    lspServers: [],
    unavailable: { capabilities: [], mcpServers: [], lspServers: [] },
  };
}
