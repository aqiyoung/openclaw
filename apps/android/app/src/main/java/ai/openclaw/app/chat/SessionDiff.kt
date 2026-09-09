package ai.openclaw.app.chat

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private fun JsonElement?.asObjectOrNull(): JsonObject? = this as? JsonObject

private fun JsonElement?.asArrayOrNull(): JsonArray? = this as? JsonArray

private fun JsonElement?.asStringOrNull(): String? =
  (this as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonElement?.asLongOrNull(): Long? =
  (this as? JsonPrimitive)?.content?.trim()?.toLongOrNull()

private fun JsonElement?.asBooleanOrNull(): Boolean? =
  (this as? JsonPrimitive)?.content?.trim()?.toBooleanStrictOrNull()

enum class SessionDiffFileStatus(val wireValue: String) {
  Added("added"),
  Modified("modified"),
  Deleted("deleted"),
  Renamed("renamed"),
  ;

  companion object {
    fun fromWire(value: String?): SessionDiffFileStatus? {
      val normalized = value?.trim()?.lowercase() ?: return null
      return entries.firstOrNull { it.wireValue == normalized }
    }
  }
}

data class SessionDiffCommit(
  val sha: String,
  val subject: String,
)

data class SessionDiffFile(
  val path: String,
  val status: SessionDiffFileStatus,
  val additions: Int,
  val deletions: Int,
  val oldPath: String? = null,
  val binary: Boolean = false,
  val untracked: Boolean = false,
  /** Unified patch text when the gateway can produce one; null for binary or oversized files. */
  val patch: String? = null,
  val truncated: Boolean = false,
)

data class SessionsDiffResult(
  val sessionKey: String,
  val files: List<SessionDiffFile>,
  val additions: Int,
  val deletions: Int,
  val root: String? = null,
  val branch: String? = null,
  val baseRef: String? = null,
  val aheadCount: Int? = null,
  val commits: List<SessionDiffCommit> = emptyList(),
  val truncated: Boolean = false,
  val unavailableReason: String? = null,
)

private fun parseSessionDiffFile(element: JsonElement): SessionDiffFile? {
  val obj = element.asObjectOrNull() ?: return null
  val path = obj["path"].asStringOrNull()?.trim()?.takeIf { it.isNotEmpty() } ?: return null
  val status = SessionDiffFileStatus.fromWire(obj["status"].asStringOrNull()) ?: return null
  return SessionDiffFile(
    path = path,
    status = status,
    additions = obj["additions"].asLongOrNull()?.coerceAtLeast(0L)?.toInt() ?: 0,
    deletions = obj["deletions"].asLongOrNull()?.coerceAtLeast(0L)?.toInt() ?: 0,
    oldPath = obj["oldPath"].asStringOrNull()?.trim()?.takeIf { it.isNotEmpty() },
    binary = obj["binary"].asBooleanOrNull() ?: false,
    untracked = obj["untracked"].asBooleanOrNull() ?: false,
    patch = obj["patch"].asStringOrNull(),
    truncated = obj["truncated"].asBooleanOrNull() ?: false,
  )
}

private fun parseSessionDiffCommit(element: JsonElement): SessionDiffCommit? {
  val obj = element.asObjectOrNull() ?: return null
  val sha = obj["sha"].asStringOrNull()?.trim()?.takeIf { it.isNotEmpty() } ?: return null
  return SessionDiffCommit(sha = sha, subject = obj["subject"].asStringOrNull()?.trim() ?: "")
}

fun parseSessionsDiff(element: JsonElement?): SessionsDiffResult? {
  val payload = element.asObjectOrNull() ?: return null
  val sessionKey =
    payload["sessionKey"].asStringOrNull()?.trim()?.takeIf { it.isNotEmpty() } ?: return null
  val files = payload["files"].asArrayOrNull()?.mapNotNull(::parseSessionDiffFile) ?: emptyList()
  return SessionsDiffResult(
    sessionKey = sessionKey,
    files = files,
    additions = payload["additions"].asLongOrNull()?.coerceAtLeast(0L)?.toInt() ?: 0,
    deletions = payload["deletions"].asLongOrNull()?.coerceAtLeast(0L)?.toInt() ?: 0,
    root = payload["root"].asStringOrNull()?.trim()?.takeIf { it.isNotEmpty() },
    branch = payload["branch"].asStringOrNull()?.trim()?.takeIf { it.isNotEmpty() },
    baseRef = payload["baseRef"].asStringOrNull()?.trim()?.takeIf { it.isNotEmpty() },
    aheadCount = payload["aheadCount"].asLongOrNull()?.coerceAtLeast(0L)?.toInt(),
    commits = payload["commits"].asArrayOrNull()?.mapNotNull(::parseSessionDiffCommit) ?: emptyList(),
    truncated = payload["truncated"].asBooleanOrNull() ?: false,
    unavailableReason =
      payload["unavailableReason"].asStringOrNull()?.trim()?.takeIf { it.isNotEmpty() },
  )
}

enum class DiffLineKind {
  Hunk,
  Context,
  Add,
  Remove,
}

data class DiffLine(
  val kind: DiffLineKind,
  val text: String,
  val oldNumber: Int? = null,
  val newNumber: Int? = null,
)

private val HUNK_HEADER = Regex("""^@@\s*-(\d+)(?:,\d+)?\s+\+(\d+)(?:,\d+)?\s*@@""")

/**
 * Splits a git unified patch into renderable rows. File headers (`diff --git`, `---`, `+++`) and
 * index lines are dropped so only real content rows survive.
 */
fun parseUnifiedPatch(patch: String?): List<DiffLine> {
  if (patch.isNullOrBlank()) return emptyList()
  val rows = ArrayList<DiffLine>()
  var oldNumber = 0
  var newNumber = 0
  for (raw in patch.lineSequence()) {
    if (raw.isEmpty()) continue
    when {
      raw.startsWith("diff --git") || raw.startsWith("index ") -> Unit
      raw.startsWith("--- ") || raw.startsWith("+++ ") -> Unit
      raw.startsWith("@@") -> {
        val match = HUNK_HEADER.find(raw)
        oldNumber = match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        newNumber = match?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 0
        rows.add(DiffLine(kind = DiffLineKind.Hunk, text = raw))
      }
      raw.startsWith("\\") -> Unit
      raw.startsWith("+") -> {
        rows.add(
          DiffLine(kind = DiffLineKind.Add, text = raw.removePrefix("+"), newNumber = newNumber)
        )
        newNumber += 1
      }
      raw.startsWith("-") -> {
        rows.add(
          DiffLine(kind = DiffLineKind.Remove, text = raw.removePrefix("-"), oldNumber = oldNumber)
        )
        oldNumber += 1
      }
      else -> {
        val text = if (raw.startsWith(" ")) raw.removePrefix(" ") else raw
        rows.add(
          DiffLine(
            kind = DiffLineKind.Context,
            text = text,
            oldNumber = oldNumber,
            newNumber = newNumber,
          )
        )
        oldNumber += 1
        newNumber += 1
      }
    }
  }
  return rows
}
