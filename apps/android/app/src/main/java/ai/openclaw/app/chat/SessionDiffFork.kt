package ai.openclaw.app.chat

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
