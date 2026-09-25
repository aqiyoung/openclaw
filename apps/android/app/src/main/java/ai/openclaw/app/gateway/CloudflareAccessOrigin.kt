package ai.openclaw.app.gateway

import java.net.URL

/**
 * Cloudflare Access origin: a constrained HTTPS/WSS authority without credentials,
 * query, or fragment. Mirrors the iOS CloudflareAccessOrigin contract.
 */
data class CloudflareAccessOrigin(
  val url: URL,
) {
  companion object {
    private val ALLOWED_SCHEMES = setOf("https", "wss")
    private const val MAX_URL_LENGTH = 4096

    fun parse(raw: String): CloudflareAccessOrigin {
      val url = URL(raw)
      return parse(url)
    }

    fun parse(url: URL): CloudflareAccessOrigin {
      val scheme = url.protocol?.lowercase()
      if (scheme !in ALLOWED_SCHEMES) throw CloudflareAccessError.InvalidGateway
      val host = url.host?.lowercase()
      if (host.isNullOrBlank()) throw CloudflareAccessError.InvalidGateway
      if (url.username != null || url.password != null) throw CloudflareAccessError.InvalidGateway
      if (url.query != null) throw CloudflareAccessError.InvalidGateway
      if (url.ref != null) throw CloudflareAccessError.InvalidGateway
      val port = url.port.let { if (it < 0) -1 else it }
      if (port != -1 && port !in 1..65535) throw CloudflareAccessError.InvalidGateway
      if (url.toString().length > MAX_URL_LENGTH) throw CloudflareAccessError.InvalidGateway

      // Normalize: force https, lowercase host, strip default port.
      val normalized = URL("https", host, if (port == 443 || port == -1) -1 else port, url.path)
      return CloudflareAccessOrigin(normalized)
    }
  }

  fun contains(other: URL): Boolean {
    // Resource requests may have queries; credentials and fragments never identify an origin.
    if (other.username != null || other.password != null) return false
    if (other.ref != null) return false
    val otherHost = other.host?.lowercase() ?: return false
    if (otherHost != url.host) return false
    val otherPort = if (other.port < 0) -1 else other.port
    val thisPort = if (url.port < 0) -1 else url.port
    if (otherPort != thisPort) return false
    return true
  }
}