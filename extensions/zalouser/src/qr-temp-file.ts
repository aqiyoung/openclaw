// Zalouser plugin module implements qr temp file behavior.
import fsp from "node:fs/promises";
import { buildRandomTempFilePath } from "openclaw/plugin-sdk/temp-path";

export async function writeQrDataUrlToTempFile(
  qrDataUrl: string,
  profile: string,
): Promise<string | null> {
  const trimmed = qrDataUrl.trim();
  const match = trimmed.match(/^data:image\/png;base64,(.+)$/i);
  const base64 = (match?.[1] ?? "").trim();
  if (!base64) {
    return null;
  }
  const safeProfile = profile.replace(/[^a-zA-Z0-9_-]+/g, "-") || "default";
  // The caller presents this path after return, so it owns the file's later cleanup.
  const filePath = buildRandomTempFilePath({
    prefix: `openclaw-zalouser-qr-${safeProfile}`,
    extension: ".png",
  });
  await fsp.writeFile(filePath, Buffer.from(base64, "base64"), { mode: 0o600 });
  return filePath;
}
