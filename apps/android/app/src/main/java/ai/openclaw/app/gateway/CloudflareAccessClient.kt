package ai.openclaw.app.gateway

import java.net.URL
import java.util.Date

/**
 * Cloudflare Access discovery + session verification client.
 * Mirrors the iOS CloudflareAccessClient contract.
 *
 * Flow:
 * 1. discover() — probe gateway, fetch Cf-Access-Metadata, verify JWT, return application
 * 2. verifiedSession() — verify app token, fetch identity, return session
 * 3. keys() — fetch JWKS from issuer
 */
object CloudflareAccessClient {

  private const val MAX_RESPONSE_BYTES = 1_048_576
  private val USER_AGENT = "OpenClaw CloudflareAccess (cloudflared/2026.8.3)"

  /** Check if an HTTP response is a Cloudflare Access challenge. */
  fun isChallenge(response: Int, responseURL: URL?, origin: CloudflareAccessOrigin): Boolean {
    if (responseURL == null || !origin.contains(responseURL)) return false
    // 302 redirect to /cdn-cgi/access/login is a challenge hint.
    if (response == 302) return true
    // WWW-Authenticate: cloudflare-access bearer="..." is a challenge.
    if (response in setOf(301, 302, 303, 307, 308, 401, 403)) return true
    return false
  }

  /**
   * Discover a Cloudflare Access application at the gateway URL.
   * Returns null if the gateway is not fronted by Cloudflare Access.
   */
  suspend fun discover(
    gatewayURL: URL,
    session: CloudflareAccessSession? = null,
    request: suspend (URL, Map<String, String>, String?) -> Triple<Int, Map<String, String>, ByteArray?>,
  ): CloudflareAccessApplication? {
    val origin = try { CloudflareAccessOrigin.parse(gatewayURL) } catch (_: Exception) { return null }
    val url = URL("https", origin.url.host, origin.url.port, "/")

    // Probe: send Cf-Access-Token if we have a session.
    val probeHeaders = mutableMapOf<String, String>()
    session?.authorizationHeader(url)?.let { probeHeaders["Cf-Access-Token"] = it }
    val (probeStatus, _, _) = request(url, probeHeaders, null)
    if (!isChallenge(probeStatus, url, origin)) return null

    // Metadata request.
    val metadataHeaders = mutableMapOf<String, String>()
    metadataHeaders["Cf-Access-Metadata-Request"] = "true"
    metadataHeaders["User-Agent"] = USER_AGENT
    val (metadataStatus, metadataHeadersMap, _) = request(url, metadataHeaders, "HEAD")
    if (metadataStatus != 200) throw CloudflareAccessError.InvalidApplication
    val token = metadataHeadersMap["Cf-Access-Metadata"] ?: throw CloudflareAccessError.InvalidApplication

    return try {
      val metadata = CloudflareAccessJWT.decode(CloudflareAccessJWT.Metadata::class.java, token)
      val application = CloudflareAccessJWT.application(metadata, origin)
      // Verify the metadata signature.
      val jwks = keys(application)
      CloudflareAccessJWT.verify(token, jwks)
      application
    } catch (e: CloudflareAccessError) {
      throw e
    } catch (_: Exception) {
      throw CloudflareAccessError.InvalidApplication
    }
  }

  /**
   * Verify an app token and fetch identity to build a session.
   */
  suspend fun verifiedSession(
    token: String,
    application: CloudflareAccessApplication,
    request: suspend (URL, Map<String, String>, String?) -> Triple<Int, Map<String, String>, ByteArray?>,
  ): CloudflareAccessSession {
    // Verify the token signature.
    val jwks = keys(application, request)
    CloudflareAccessJWT.verify(token, jwks)
    val claims = CloudflareAccessJWT.appClaims(token, application)

    // Fetch identity to confirm the subject.
    val identityURL = application.origin.url.toURI().resolve("cdn-cgi/access/get-identity").toURL()
    val identityHeaders = mapOf("Cookie" to "CF_Authorization=$token")
    val (identityStatus, _, identityData) = request(identityURL, identityHeaders, null)
    if (identityStatus != 200 || identityData == null) throw CloudflareAccessError.InvalidSession

    val identityJson = String(identityData)
    val userUUID = Regex(""""user_uuid"\s*:\s*"([^"]+)"""").find(identityJson)?.groupValues?.get(1)
      ?: throw CloudflareAccessError.InvalidSession
    if (userUUID != claims.sub) throw CloudflareAccessError.InvalidSession

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

  private suspend fun keys(
    application: CloudflareAccessApplication,
    request: suspend (URL, Map<String, String>, String?) -> Triple<Int, Map<String, String>, ByteArray?>,
  ): ByteArray {
    val host = application.issuer.host ?: throw CloudflareAccessError.InvalidApplication
    if (CloudflareAccessJWT.issuer(host) != application.issuer) throw CloudflareAccessError.InvalidApplication
    val certsURL = application.issuer.toURI().resolve("cdn-cgi/access/certs").toURL()
    val (status, _, data) = request(certsURL, emptyMap(), null)
    if (status != 200 || data == null) throw CloudflareAccessError.InvalidApplication
    return data
  }
}