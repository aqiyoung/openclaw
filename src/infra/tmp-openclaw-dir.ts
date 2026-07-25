// Creates temporary OpenClaw directories for runtime scratch work.
import { resolveSecureTempRoot } from "@openclaw/fs-safe/temp";

/** Preferred shared OpenClaw temp root on POSIX systems when ownership and permissions are safe. */
export const DEFAULT_POSIX_TMP_ROOT = "/tmp/openclaw";

type SecureDirStat = {
  isDirectory(): boolean;
  isSymbolicLink(): boolean;
  mode?: number;
  uid?: number;
};

/** Injectable filesystem/platform hooks for resolving the preferred temp root in tests. */
export type ResolvePreferredOpenClawTmpDirOptions = {
  accessSync?: (path: string, mode?: number) => void;
  chmodSync?: (path: string, mode: number) => void;
  getuid?: () => number | undefined;
  lstatSync?: (path: string) => SecureDirStat;
  mkdirSync?: (path: string, opts: { recursive: boolean; mode?: number }) => void;
  platform?: NodeJS.Platform;
  tmpdir?: () => string;
  warn?: (message: string) => void;
};

/** Resolves a safe OpenClaw temp root, falling back to user-scoped os.tmpdir paths when needed. */
export function resolvePreferredOpenClawTmpDir(
  options: ResolvePreferredOpenClawTmpDirOptions = {},
): string {
  return resolveSecureTempRoot({
    ...options,
    preferredDir: DEFAULT_POSIX_TMP_ROOT,
    fallbackPrefix: "openclaw",
    warningPrefix: "[openclaw]",
    unsafeFallbackLabel: "OpenClaw temp dir",
    skipPreferredOnWindows: true,
  });
}
