package ai.openclaw.app.gateway

import java.net.URL
import java.util.Date

/**
 * A verified Cloudflare Access session. Mirrors the iOS CloudflareAccessSession contract.
 *
 * The token is opaque to the UI layer; only the authorization header is exposed.
 */
data class CloudflareAccessSession(
  val origin: CloudflareAccessOrigin,
  val issuer: URL,
  val audience: String,
  val subject: String,
  val expiresAt: Date,
  val token: String,
) {
  fun authorizationHeader(for: URL, now: Date = Date()): String? {
    if (!origin.contains(for)) return null
    if (expiresAt <= now) return null
    return token
  }

  fun validate(now: Date = Date()) {
    val application = CloudflareAccessApplication(origin, issuer, audience)
    val claims = CloudflareAccessJWT.appClaims(token, application)
    if (claims.sub != subject) throw CloudflareAccessError.InvalidSession
    if (expiresAt.time / 1000.0 != claims.exp) throw CloudflareAccessError.InvalidSession
  }

  companion object {
    /** Build a session from a verified app token + application. */
    fun fromToken(
      application: CloudflareAccessApplication,
      token: String,
    ): CloudflareAccessSession {
      val claims = CloudflareAccessJWT.appClaims(token, application)
      val expiresAt = Date((claims.exp * 1000).toLong())
      return CloudflareAccessSession(
        origin = application.origin,
        issuer = application.issuer,
        audience = application.audience,
        subject = claims.sub,
        expiresAt = expiresAt,
        token = token,
      )
    }
  }
}