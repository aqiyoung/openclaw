const BUZZ_CHANNEL_ID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/iu;

export function normalizeBuzzTarget(target: string): string {
  return target
    .trim()
    .replace(/^buzz:/iu, "")
    .replace(/^channel:/iu, "");
}

export function parseBuzzTarget(target: string): string {
  const channelId = normalizeBuzzTarget(target);
  if (!BUZZ_CHANNEL_ID_PATTERN.test(channelId)) {
    throw new Error("Buzz target must be a channel UUID");
  }
  return channelId.toLowerCase();
}

export function buildBuzzTarget(channelId: string): string {
  return `buzz:${parseBuzzTarget(channelId)}`;
}

export function looksLikeBuzzTarget(target: string): boolean {
  try {
    parseBuzzTarget(target);
    return true;
  } catch {
    return false;
  }
}
