package ai.openclaw.app.gateway

import android.util.Base64
import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.generators.X25519KeyPairGenerator
import org.bouncycastle.crypto.params.X25519KeyGenerationParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.crypto.macs.Poly1305
import org.bouncycastle.crypto.engines.XSalsa20Engine
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV
import java.net.URL
import java.security.SecureRandom
import kotlin.math.min

/**
 * Cloudflare Access encrypted token transfer.
 * Mirrors the iOS CloudflareAccessTransfer contract using BCProv (X25519 + XSalsa20 + Poly1305).
 *
 * Flow:
 * 1. Generate ephemeral X25519 keypair
 * 2. Open browser to /cdn-cgi/access/cli with public key + audience
 * 3. Poll https://login.cloudflareaccess.org/transfer/<publicKey>
 * 4. Decrypt the envelope with X25519 shared secret → XSalsa20 → Poly1305
 * 5. Extract app_token from plaintext
 */
object CloudflareAccessTransfer {

  private const val MAX_BODY_BYTES = 131_072
  private const val PUBLIC_KEY_BYTES = 32
  private const val SECRET_KEY_BYTES = 32
  private const val NONCE_BYTES = 24
  private const val MAC_BYTES = 16
  private const val ENVELOPE_MIN_BYTES = NONCE_BYTES + MAC_BYTES + 1

  private val random = SecureRandom()

  /** Build the browser URL for sign-in. */
  fun browserURL(application: CloudflareAccessApplication, publicKey: String): URL {
    val origin = application.origin.url
    val redirectURL = URL(origin.protocol, origin.host, origin.port, origin.path.ifEmpty { "/" })
    val redirect = redirectURL.toString()
    val cliURL = URL(
      origin.protocol,
      origin.host,
      origin.port,
      "/cdn-cgi/access/cli?token=${java.net.URLEncoder.encode(publicKey, "UTF-8")}" +
        "&aud=${java.net.URLEncoder.encode(application.audience, "UTF-8")}" +
        "&redirect_url=${java.net.URLEncoder.encode(redirect, "UTF-8")}" +
        "&send_org_token=true" +
        "&edge_token_transfer=true" +
        "&close_interstitial=true",
    )
    return cliURL
  }

  /** Build the transfer polling URL. */
  fun transferURL(publicKey: String): URL =
    URL("https://login.cloudflareaccess.org/transfer/$publicKey")

  /** Encode a 32-byte X25519 public key as URL-safe base64 (no padding). */
  fun publicKeyString(bytes: ByteArray): String {
    if (bytes.size != PUBLIC_KEY_BYTES) throw CloudflareAccessError.LoginFailed
    return Base64.encodeToString(bytes, Base64.NO_WRAP).replace("+", "-").replace("/", "_").trim('=')
  }

  /**
   * Decrypt the transfer envelope and extract the app token.
   *
   * @param body Raw HTTP response body (base64-encoded JSON envelope)
   * @param servicePublicKey Base64URL-encoded peer public key (44 chars, no padding)
   * @param secretKey Our 32-byte X25519 private key
   * @return The app_token string
   */
  fun appToken(body: ByteArray, servicePublicKey: String, secretKey: ByteArray): String {
    if (body.size > MAX_BODY_BYTES) throw CloudflareAccessError.LoginFailed
    if (servicePublicKey.length != 44) throw CloudflareAccessError.LoginFailed
    if (secretKey.size != SECRET_KEY_BYTES) throw CloudflareAccessError.LoginFailed

    val bodyString = String(body, Charsets.UTF_8)
    if (!isBase64(bodyString, urlSafe = false)) throw CloudflareAccessError.LoginFailed
    if (!isBase64(servicePublicKey, urlSafe = true)) throw CloudflareAccessError.LoginFailed

    val envelope = base64Decode(bodyString, urlSafe = false)
    if (envelope.size < ENVELOPE_MIN_BYTES) throw CloudflareAccessError.LoginFailed

    val peerBytes = base64Decode(servicePublicKey, urlSafe = true)
    if (peerBytes.size != PUBLIC_KEY_BYTES) throw CloudflareAccessError.LoginFailed

    // X25519 shared secret.
    val agreement = X25519Agreement()
    agreement.init(X25519PrivateKeyParameters(secretKey, 0))
    val sharedSecret = ByteArray(PUBLIC_KEY_BYTES)
    agreement.generateSharedSecret(X25519PublicKeyParameters(peerBytes, 0), sharedSecret)

    // Derive XSalsa20 key from shared secret (first 32 bytes).
    val key = sharedSecret.copyOfRange(0, 32)

    // Envelope: nonce (24) || ciphertext || Poly1305 MAC (16)
    val nonce = envelope.copyOfRange(0, NONCE_BYTES)
    val mac = envelope.copyOfRange(envelope.size - MAC_BYTES, envelope.size)
    val ciphertext = envelope.copyOfRange(NONCE_BYTES, envelope.size - MAC_BYTES)

    // XSalsa20-Poly1305 decryption.
    val poly1305 = Poly1305()
    poly1305.init(KeyParameter(key.copyOfRange(0, 32)))
    // Poly1305 over nonce || ciphertext
    val macInput = nonce + ciphertext
    val computedMac = ByteArray(MAC_BYTES)
    poly1305.update(macInput, 0, macInput.size)
    poly1305.doFinal(computedMac, 0)
    if (!computedMac.contentEquals(mac)) throw CloudflareAccessError.LoginFailed

    // XSalsa20 decryption.
    val cipher = XSalsa20Engine()
    cipher.init(true, ParametersWithIV(KeyParameter(key), nonce))
    val plaintext = ByteArray(ciphertext.size)
    cipher.processBytes(ciphertext, 0, ciphertext.size, plaintext, 0)

    val plaintextStr = String(plaintext, Charsets.UTF_8)
    // Extract app_token from JSON: {"app_token": "...", "org_token": "..."}
    val appToken = Regex(""""app_token"\s*:\s*"([^"]+)"""").find(plaintextStr)?.groupValues?.get(1)
      ?: throw CloudflareAccessError.LoginFailed
    if (appToken.isEmpty() || appToken.length > 32768) throw CloudflareAccessError.LoginFailed

    // Zero out sensitive material.
    sharedSecret.fill(0)
    plaintext.fill(0)

    return appToken
  }

  /** Generate an ephemeral X25519 keypair. Returns (publicKey, secretKey). */
  fun generateKeyPair(): Pair<ByteArray, ByteArray> {
    val generator = X25519KeyPairGenerator()
    generator.init(X25519KeyGenerationParameters(random))
    val keyPair = generator.generateKeyPair()
    val pub = (keyPair.public as X25519PublicKeyParameters).encoded
    val priv = (keyPair.private as X25519PrivateKeyParameters).encoded
    return pub to priv
  }

  private fun isBase64(value: String, urlSafe: Boolean): Boolean {
    for (i in value.indices) {
      val c = value[i]
      when (c) {
        in 'A'..'Z', in 'a'..'z', in '0'..'9', '=' -> continue
        '-', '_' -> if (!urlSafe) return false
        '+', '/' -> if (urlSafe) return false
        else -> return false
      }
    }
    return true
  }

  private fun base64Decode(value: String, urlSafe: Boolean): ByteArray {
    val normalized = if (urlSafe) value.replace("-", "+").replace("_", "/") else value
    val padding = (4 - normalized.length % 4) % 4
    return Base64.decode(normalized + "=".repeat(padding), Base64.NO_WRAP)
  }
}