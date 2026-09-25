package ai.openclaw.app.gateway

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.RSAPublicKeySpec

/**
 * Cloudflare Access JWT handling. Mirrors the iOS CloudflareAccessJWT contract.
 *
 * - Metadata tokens: type "match", hostname, auth_domain, aud, iat
 * - App tokens: type "app", iss, aud, sub, exp, nbf
 * - Verification: RS256 signature against JWKS
 */
object CloudflareAccessJWT {

  data class Metadata(
    val type: String,
    val hostname: String,
    val authDomain: String,
    val aud: String,
    val iat: Double,
  )

  data class AppClaims(
    val iss: String,
    val aud: List<String>,
    val type: String,
    val sub: String,
    val exp: Double,
    val nbf: Double?,
  )

  private data class Header(
    val alg: String,
    val kid: String,
    val crit: List<String>?,
  )

  private data class JwksKey(
    val kty: String,
    val kid: String?,
    val alg: String?,
    val use: String?,
    val n: String?,
    val e: String?,
  )

  private data class KeySet(
    val keys: List<JwksKey>,
  )

  /** Decode a JWT's payload claims without verifying the signature. */
  fun <T : Any> decode(type: Class<T>, token: String): T {
    val parts = parts(token)
    val payloadJson = String(base64URLDecode(parts[1]), StandardCharsets.UTF_8)
    val obj = JSONObject(payloadJson)
    return when (type) {
      Metadata::class.java -> Metadata(
        type = obj.getString("type"),
        hostname = obj.getString("hostname"),
       authDomain = obj.getString("auth_domain"),
        aud = obj.getString("aud"),
        iat = obj.getDouble("iat"),
      ) as T
      AppClaims::class.java -> AppClaims(
        iss = obj.getString("iss"),
        aud = when (val audValue = obj.get("aud")) {
          is JSONArray -> (0 until audValue.length()).map { audValue.getString(it) }
          else -> listOf(obj.getString("aud"))
        },
        type = obj.getString("type"),
        sub = obj.getString("sub"),
        exp = obj.getDouble("exp"),
        nbf = if (obj.has("nbf")) obj.getDouble("nbf") else null,
      ) as T
      Header::class.java -> Header(
        alg = obj.getString("alg"),
        kid = obj.getString("kid"),
        crit = if (obj.has("crit")) {
          val arr = obj.getJSONArray("crit")
          (0 until arr.length()).map { arr.getString(it) }
        } else null,
      ) as T
      else -> throw CloudflareAccessError.InvalidSession
    }
  }

  /** Verify a JWT's RS256 signature against JWKS data. */
  fun verify(token: String, jwks: ByteArray) {
    val parts = parts(token)
    val header = decode(Header::class.java, token)
    if (header.alg != "RS256") throw CloudflareAccessError.InvalidSession
    if (header.kid.isEmpty()) throw CloudflareAccessError.InvalidSession
    if (header.kid.length > 512) throw CloudflareAccessError.InvalidSession
    if (header.crit != null && header.crit.isNotEmpty()) throw CloudflareAccessError.InvalidSession

    val keySetJson = String(jwks, StandardCharsets.UTF_8)
    val keysArray = JSONObject(keySetJson).getJSONArray("keys")
    val keySet = KeySet(
      keys = (0 until keysArray.length()).map { i ->
        val k = keysArray.getJSONObject(i)
        JwksKey(
          kty = k.getString("kty"),
          kid = if (k.has("kid") && !k.isNull("kid")) k.getString("kid") else null,
          alg = if (k.has("alg") && !k.isNull("alg")) k.getString("alg") else null,
          use = if (k.has("use") && !k.isNull("use")) k.getString("use") else null,
          n = if (k.has("n") && !k.isNull("n")) k.getString("n") else null,
          e = if (k.has("e") && !k.isNull("e")) k.getString("e") else null,
        )
      },
    )

    if (keySet.keys.size > 64) throw CloudflareAccessError.InvalidSession
    val key = keySet.keys.firstOrNull {
      it.kid == header.kid && it.kty == "RSA" &&
        (it.alg == null || it.alg == "RS256") && (it.use == null || it.use == "sig")
    } ?: throw CloudflareAccessError.InvalidSession

    val nBytes = base64URLDecodeString(key.n ?: throw CloudflareAccessError.InvalidSession)
    val eBytes = base64URLDecodeString(key.e ?: throw CloudflareAccessError.InvalidSession)
    if (nBytes.size !in 256..1024) throw CloudflareAccessError.InvalidSession
    if (eBytes.size !in 1..8) throw CloudflareAccessError.InvalidSession

    val keyFactory = KeyFactory.getInstance("RSA")
    val publicKey = keyFactory.generatePublic(RSAPublicKeySpec(
      java.math.BigInteger(1, nBytes),
      java.math.BigInteger(1, eBytes),
    ))

    val signingInput = "${parts[0]}.${parts[1]}".toByteArray(StandardCharsets.UTF_8)
    val signatureBytes = base64URLDecode(parts[2])
    val signature = Signature.getInstance("SHA256withRSA")
    signature.initVerify(publicKey)
    signature.update(signingInput)
    if (!signature.verify(signatureBytes)) throw CloudflareAccessError.InvalidSession
  }

  /** Derive a CloudflareAccessApplication from verified metadata claims. */
  fun application(metadata: Metadata, origin: CloudflareAccessOrigin): CloudflareAccessApplication {
    val now = System.currentTimeMillis() / 1000.0
    if (metadata.type != "match") throw CloudflareAccessError.InvalidApplication
    if (metadata.hostname.lowercase() != origin.url.host?.lowercase()) throw CloudflareAccessError.InvalidApplication
    if (metadata.aud.isEmpty() || metadata.aud.length > 512) throw CloudflareAccessError.InvalidApplication
    if (metadata.iat <= 0) throw CloudflareAccessError.InvalidApplication
    if (metadata.iat < now - 86400) throw CloudflareAccessError.InvalidApplication
    if (metadata.iat > now + 300) throw CloudflareAccessError.InvalidApplication
    val issuer = issuer(metadata.authDomain) ?: throw CloudflareAccessError.InvalidApplication
    return CloudflareAccessApplication(origin, issuer, metadata.aud)
  }

  /** Extract app claims from an already-verified token. */
  fun appClaims(token: String, application: CloudflareAccessApplication): AppClaims {
    val claims = decode(AppClaims::class.java, token)
    val host = application.issuer.host ?: throw CloudflareAccessError.InvalidApplication
    val issuer = issuer(host) ?: throw CloudflareAccessError.InvalidApplication
    if (issuer != application.issuer) throw CloudflareAccessError.InvalidApplication
    if (claims.iss != application.issuer.toString()) throw CloudflareAccessError.InvalidApplication
    if (!claims.aud.contains(application.audience)) throw CloudflareAccessError.InvalidApplication
    if (claims.aud.size > 16) throw CloudflareAccessError.InvalidApplication
    if (claims.type != "app") throw CloudflareAccessError.InvalidApplication
    if (claims.sub.isEmpty() || claims.sub.length > 512) throw CloudflareAccessError.InvalidApplication
    val now = System.currentTimeMillis() / 1000.0
    if (!claims.exp.isFinite() || claims.exp <= now) throw CloudflareAccessError.InvalidSession
    if (claims.nbf != null && (!claims.nbf.isFinite() || claims.nbf > now)) throw CloudflareAccessError.InvalidSession
    return claims
  }

  /** Validate that a hostname is a Cloudflare Access auth domain. */
  fun issuer(authDomain: String): java.net.URL? {
    val host = authDomain.lowercase()
    val suffix = ".cloudflareaccess.com"
    if (!host.endsWith(suffix)) return null
    val team = host.dropLast(suffix.length)
    if (team.isEmpty() || team.length > 63) return null
    if (team.startsWith("-") || team.endsWith("-")) return null
    if (!team.all { it.isDigit() || it.isLowerCase() || it == '-' }) return null
    return try { java.net.URL("https://$host") } catch (_: Exception) { null }
  }

  // --- Internal helpers ---

  private fun parts(token: String): Array<String> {
    if (token.length > 32768) throw CloudflareAccessError.InvalidSession
    val parts = token.split(".")
    if (parts.size != 3) throw CloudflareAccessError.InvalidSession
    val headerJson = String(base64URLDecode(parts[0]), StandardCharsets.UTF_8)
    val headerObj = JSONObject(headerJson)
    if (headerObj.getString("alg") != "RS256") throw CloudflareAccessError.InvalidSession
    val kid = if (headerObj.has("kid") && !headerObj.isNull("kid")) headerObj.getString("kid") else null
      if (kid.isNullOrBlank()) throw CloudflareAccessError.InvalidSession
    if (kid.isEmpty() || kid.length > 512) throw CloudflareAccessError.InvalidSession
    return parts.toTypedArray()
  }

  private fun base64URLDecode(input: String): ByteArray {
    val base64 = input.replace("-", "+").replace("_", "/")
    val padding = (4 - base64.length % 4) % 4
    val padded = base64 + "=".repeat(padding)
    return Base64.decode(padded, Base64.NO_WRAP)
  }

  private fun base64URLDecodeString(input: String): ByteArray = base64URLDecode(input)



}
