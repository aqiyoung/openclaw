import { defineBundledChannelEntry } from "openclaw/plugin-sdk/channel-entry-contract";

export default defineBundledChannelEntry({
  id: "buzz",
  name: "Buzz",
  description: "Buzz group chat channel plugin",
  importMetaUrl: import.meta.url,
  plugin: {
    specifier: "./channel-plugin-api.js",
    exportName: "buzzPlugin",
  },
  runtime: {
    specifier: "./api.js",
    exportName: "setBuzzRuntime",
  },
});
