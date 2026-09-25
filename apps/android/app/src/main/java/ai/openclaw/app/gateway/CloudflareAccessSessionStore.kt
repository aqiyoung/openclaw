package ai.openclaw.app.gateway

import ai.openclaw.app.SecurePrefs
import java.net.URL
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Manages Cloudflare Access sessions per gateway stable ID.
 *
 * Mirrors the iOS CloudflareAccessSessionStore contract:
 * - Loads/saves sessions in EncryptedSharedPreferences via SecurePrefs
 * - Tracks sign-in state (signedOut / signingIn / authenticated / reauthenticationRequired)
 * - One session per gateway stable ID
 * - Token expiry triggers reauthenticationRequired
 */
class CloudflareAccessSessionStore(
  private val securePrefs: SecurePrefs,
) {
  enum class State {
    SignedOut,
    SigningIn,
    Authenticated,
    ReauthenticationRequired,
  }

  data class Snapshot(
    val session: CloudflareAccessSession,
    val revision: Long,
  )

  private val mutex = Mutex()
  private val sessions = ConcurrentHashMap<String, Snapshot>()
  private val states = ConcurrentHashMap<String, State>()
  private var revisionCounter = 0L

  val revision: Long
    get() = revisionCounter

  fun state(stableId: String): State =
    states[stableId] ?: State.SignedOut

  fun snapshot(stableId: String, now: Date = Date()): Snapshot? {
    if (states[stableId] == null) {
      // Lazy-load from storage.
      val token = securePrefs.loadCloudflareAccessSession(stableId)
      if (token != null) {
        try {
          // We can't fully validate without the application, but we can check expiry
          // by decoding the JWT claims.
          val claims = CloudflareAccessJWT.decode(CloudflareAccessJWT.AppClaims::class.java, token)
          val expiresAt = Date((claims.exp * 1000).toLong())
          if (expiresAt > now) {
            // Build a minimal session. The origin/issuer/audience will be re-derived
            // on next discover(), but the token is still valid.
            revisionCounter++
            val snapshot = Snapshot(
              session = CloudflareAccessSession(
                origin = CloudflareAccessOrigin.parse("https://${claims.iss}"),
                issuer = URL(claims.iss),
                audience = claims.aud.firstOrNull() ?: "",
                subject = claims.sub,
                expiresAt = expiresAt,
                token = token,
              ),
              revision = revisionCounter,
            )
            sessions[stableId] = snapshot
            states[stableId] = State.Authenticated
          } else {
            states[stableId] = State.ReauthenticationRequired
          }
        } catch (_: Exception) {
          states[stableId] = State.ReauthenticationRequired
        }
      } else {
        states[stableId] = State.SignedOut
      }
    }

    val snapshot = sessions[stableId] ?: return null
    if (snapshot.session.expiresAt <= now) {
      sessions.remove(stableId)
      states[stableId] = State.ReauthenticationRequired
      revisionCounter++
      return null
    }
    return snapshot
  }

  /**
   * Publish a newly authenticated session for a gateway.
   * Saves the token to SecurePrefs and updates in-memory state.
   */
  suspend fun publish(stableId: String, session: CloudflareAccessSession) = mutex.withLock {
    sessions.remove(stableId)
    revisionCounter++
    securePrefs.saveCloudflareAccessSession(stableId, session.token)
    revisionCounter++
    sessions[stableId] = Snapshot(session, revisionCounter)
    states[stableId] = State.Authenticated
  }

  /**
   * Mark a gateway as needing reauthentication.
   * Clears the in-memory session but keeps the stored token (it may still be valid
   * for a different origin).
   */
  suspend fun requireReauthentication(stableId: String) = mutex.withLock {
    sessions.remove(stableId)
    states[stableId] = State.ReauthenticationRequired
    revisionCounter++
  }

  /**
   * Forget a gateway's Cloudflare Access session entirely.
   * Removes the token from SecurePrefs and clears in-memory state.
   */
  suspend fun forget(stableId: String) = mutex.withLock {
    sessions.remove(stableId)
    states[stableId] = State.SignedOut
    revisionCounter++
    securePrefs.clearCloudflareAccessSession(stableId)
  }

  /**
   * Get the Cf-Access-Token header value for a gateway, if a valid session exists.
   * Returns null if no session or session expired.
   */
  fun accessToken(stableId: String, now: Date = Date()): String? {
    val snapshot = snapshot(stableId, now) ?: return null
    return snapshot.session.token
  }

  /**
   * Set the signing-in state for a gateway.
   */
  fun setSigningIn(stableId: String) {
    states[stableId] = State.SigningIn
    revisionCounter++
  }

  /**
   * Cancel an in-progress sign-in for a gateway.
   */
  fun cancelSignIn(stableId: String) {
    if (states[stableId] == State.SigningIn) {
      states[stableId] = State.ReauthenticationRequired
      revisionCounter++
    }
  }
}