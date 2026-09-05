#!/usr/bin/env node

// Syncs Canvas A2UI assets to native app directories.
// Stub: actual sync logic to be implemented when A2UI renderer is finalized.

import { parseArgs } from "node:util";

const { values } = parseArgs({
  options: {
    write: { type: "boolean", default: false },
    output: { type: "string", default: "" },
  },
  strict: false,
});

if (values.write && values.output) {
  // Future: copy bundled A2UI assets to output directory
  console.log(`[sync-native-a2ui] output: ${values.output}`);
}

console.log("[sync-native-a2ui] done");
