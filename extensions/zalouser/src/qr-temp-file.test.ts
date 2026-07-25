import fs from "node:fs/promises";
import path from "node:path";
import { afterEach, describe, expect, it } from "vitest";
import { writeQrDataUrlToTempFile } from "./qr-temp-file.js";

describe("writeQrDataUrlToTempFile", () => {
  const createdPaths: string[] = [];

  afterEach(async () => {
    await Promise.all(createdPaths.splice(0).map((filePath) => fs.rm(filePath, { force: true })));
  });

  it("writes each QR image to a private randomized temp path", async () => {
    const data = Buffer.from("qr-image");
    const dataUrl = `data:image/png;base64,${data.toString("base64")}`;
    const first = await writeQrDataUrlToTempFile(dataUrl, "profile/name");
    if (!first) {
      throw new Error("expected first QR temp path");
    }
    createdPaths.push(first);
    const second = await writeQrDataUrlToTempFile(dataUrl, "profile/name");
    if (!second) {
      throw new Error("expected second QR temp path");
    }
    createdPaths.push(second);

    expect(first).not.toBe(second);
    expect(path.basename(first)).toMatch(/^openclaw-zalouser-qr-profile-name-.*\.png$/);
    await expect(fs.readFile(first)).resolves.toEqual(data);
    if (process.platform !== "win32") {
      expect((await fs.stat(first)).mode & 0o777).toBe(0o600);
    }
  });
});
