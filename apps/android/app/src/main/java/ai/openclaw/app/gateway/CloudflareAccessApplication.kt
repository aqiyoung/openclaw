package ai.openclaw.app.gateway

/**
 * A verified Cloudflare Access application: origin + issuer + audience.
 * Mirrors the iOS CloudflareAccessApplication contract.
 */
data class CloudflareAccessApplication(
  val origin: CloudflareAccessOrigin,
  val issuer: java.net.URL,
  val audience: String,
)