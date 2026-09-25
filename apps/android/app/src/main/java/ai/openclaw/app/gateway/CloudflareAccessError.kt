package ai.openclaw.app.gateway

/**
 * Cloudflare Access sign-in errors. Mirrors the iOS CloudflareAccessError contract so the
 * UI layer can present the same messages regardless of platform.
 */
sealed class CloudflareAccessError(message: String) : Exception(message) {
  /** The gateway URL is not a valid HTTPS/WSS origin without credentials or fragments. */
  object InvalidGateway : CloudflareAccessError(
    "Enter an HTTPS gateway address without credentials, a query, or a fragment.",
  )

  /** The gateway did not return valid Cloudflare Access sign-in metadata. */
  object InvalidApplication : CloudflareAccessError(
    "This gateway did not provide valid Cloudflare Access sign-in details. Contact its administrator.",
  )

  /** The sign-in service could not be reached. */
  object ConnectionFailed : CloudflareAccessError(
    "Could not reach the gateway's sign-in service. Check your connection and try again.",
  )

  /** Browser sign-in did not complete. */
  object LoginFailed : CloudflareAccessError(
    "Browser sign-in did not complete. Check that your account can access this gateway and try again.",
  )

  /** Browser sign-in timed out. */
  object TimedOut : CloudflareAccessError(
    "Browser sign-in timed out. Start sign-in again to continue.",
  )

  /** The session could not be verified or has expired. */
  object InvalidSession : CloudflareAccessError(
    "The Cloudflare Access session could not be verified or has expired. Sign in again.",
  )

  /** The session could not be saved securely. */
  object StorageFailed : CloudflareAccessError(
    "Could not save the sign-in session securely. Unlock this device and try again.",
  )
}