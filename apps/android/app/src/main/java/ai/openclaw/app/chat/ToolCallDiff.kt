package ai.openclaw.app.chat

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Inline diff rows for tool-call rendering.
 *
 * Mirrors the web client's `ui/src/lib/chat/tool-call-diff.ts`: a producer may ship a
 * precomputed display diff, otherwise a local line diff is computed from the tool arguments.
 */
enum class ToolDiffLineKind {
  Add,
  Delete,
  Context,
  Skip,
}

data class ToolDiffLine(
  val kind: ToolDiffLineKind,
  val text: String,
  val lineNo: Int? = null,
)

data class ToolDiffPreview(
  val lines: List<ToolDiffLine>,
  val added: Int,
  val removed: Int,
  val truncated: Boolean = false,
)

private fun JsonElement?.asObjectOrNull(): JsonObject? = this as? JsonObject

private fun JsonElement?.asArrayOrNull(): JsonArray? = this as? JsonArray

private fun JsonElement?.asStringOrNull(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonObject.stringOrNull(key: String): String? = this[key].asStringOrNull()?.trim()?.takeIf { it.isNotEmpty() }

private class LocalDiff(
  val lines: List<ToolDiffLine>,
  val added: Int,
  val removed: Int,
  val complete: Boolean,
)

/** Bound diff rendering work; oversized inputs degrade to a truncation marker. */
private const val MAX_DIFF_INPUT_LINES = 600
private const val MAX_DIFF_RENDER_LINES = 400
private const val MAX_LOCAL_DIFF_PAIRS = 8
private const val MAX_LOCAL_DIFF_INPUT_CHARS = 120_000

private val DIFF_TRUNCATED_MARKER = Regex("""^\s*\.\.\.\(truncated\)\.\.\.\s*$""")
private val DIFF_SKIP_MARKER = Regex("""^\s*\.\.\.\s*$""")
private val DIFF_ROW = Regex("""^([+\- ])\s*(\d+) ?(.*)$""", RegexOption.DOT_MATCHES_ALL)

private val EDIT_TOOL_NAMES = setOf("edit", "edit_file", "multiedit", "multi_edit", "notebookedit", "notebook_edit")
private val TEXT_EDITOR_TOOL_NAMES = setOf("str_replace_editor", "str_replace_based_edit_tool")
private val WRITE_TOOL_NAMES = setOf("write", "write_file", "create_file")
private val PATCH_TOOL_NAMES = setOf("apply_patch", "applypatch", "patch")

private val OLD_TEXT_KEYS = listOf("oldText", "old_string", "oldString", "old_str")
private val NEW_TEXT_KEYS = listOf("newText", "new_string", "newString", "new_str")
private val PATH_KEYS = listOf("path", "file_path", "filePath", "file", "filepath", "filename", "notebook_path")

private fun diffStat(lines: List<ToolDiffLine>): Pair<Int, Int> {
  var added = 0
  var removed = 0
  for (line in lines) {
    when (line.kind) {
      ToolDiffLineKind.Add -> added += 1
      ToolDiffLineKind.Delete -> removed += 1
      else -> Unit
    }
  }
  return added to removed
}

private fun splitDiffLines(text: String): List<String> {
  val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
  // Empty snippets are zero lines: deletions and insertions from an empty side must not
  // produce a phantom blank row in the diff.
  if (normalized.isEmpty()) return emptyList()
  val lines = normalized.split('\n').toMutableList()
  if (lines.size > 1 && lines.last().isEmpty()) lines.removeAt(lines.lastIndex)
  return lines
}

internal fun countTextLines(content: String): Int = splitDiffLines(content).size

/** Parse a producer-recorded display diff: `+457 text`, `-455 text`, ` 456 text`, `...` gaps. */
internal fun parseDiffDetailsString(diff: String): ToolDiffPreview? {
  if (diff.isBlank()) return null
  val lines = ArrayList<ToolDiffLine>()
  var truncated = false
  for (raw in diff.split('\n')) {
    if (raw.isEmpty()) continue
    if (DIFF_TRUNCATED_MARKER.matches(raw)) {
      truncated = true
      lines.add(ToolDiffLine(kind = ToolDiffLineKind.Skip, text = ""))
      continue
    }
    if (DIFF_SKIP_MARKER.matches(raw)) {
      lines.add(ToolDiffLine(kind = ToolDiffLineKind.Skip, text = ""))
      continue
    }
    val match = DIFF_ROW.matchEntire(raw) ?: return null
    val values = match.groupValues
    lines.add(
      ToolDiffLine(
        kind =
          when (values[1]) {
            "+" -> ToolDiffLineKind.Add
            "-" -> ToolDiffLineKind.Delete
            else -> ToolDiffLineKind.Context
          },
        text = values[3],
        lineNo = values[2].toIntOrNull(),
      ),
    )
    if (lines.size > MAX_DIFF_RENDER_LINES) {
      lines.add(ToolDiffLine(kind = ToolDiffLineKind.Skip, text = ""))
      truncated = true
      break
    }
  }
  if (!lines.any { it.kind == ToolDiffLineKind.Add || it.kind == ToolDiffLineKind.Delete }) return null
  val (added, removed) = diffStat(lines)
  return ToolDiffPreview(lines = lines, added = added, removed = removed, truncated = truncated)
}

private fun compactLineDiff(
  lines: List<ToolDiffLine>,
  inputTruncated: Boolean,
  compactUnchanged: Boolean,
): List<ToolDiffLine> {
  if (!compactUnchanged && lines.size <= MAX_DIFF_RENDER_LINES && !inputTruncated) return lines
  val hasChange = lines.any { it.kind == ToolDiffLineKind.Add || it.kind == ToolDiffLineKind.Delete }
  if (!hasChange) {
    if (compactUnchanged && !inputTruncated) return emptyList()
    return if (inputTruncated) {
      listOf(ToolDiffLine(kind = ToolDiffLineKind.Skip, text = ""))
    } else {
      lines.take(MAX_DIFF_RENDER_LINES) + ToolDiffLine(kind = ToolDiffLineKind.Skip, text = "")
    }
  }
  val keep = BooleanArray(lines.size)
  for (index in lines.indices) {
    val kind = lines[index].kind
    if (kind != ToolDiffLineKind.Add && kind != ToolDiffLineKind.Delete) continue
    val start = maxOf(0, index - 3)
    val end = minOf(lines.size, index + 4)
    for (cursor in start until end) keep[cursor] = true
  }
  val preview = ArrayList<ToolDiffLine>()
  var gap = false
  var clipped = inputTruncated
  for (index in lines.indices) {
    if (!keep[index]) {
      gap = true
      clipped = true
      continue
    }
    if (gap && preview.lastOrNull()?.kind != ToolDiffLineKind.Skip) {
      preview.add(ToolDiffLine(kind = ToolDiffLineKind.Skip, text = ""))
    }
    gap = false
    if (preview.size >= MAX_DIFF_RENDER_LINES) {
      clipped = true
      break
    }
    preview.add(lines[index])
  }
  if (clipped && preview.lastOrNull()?.kind != ToolDiffLineKind.Skip) {
    preview.add(ToolDiffLine(kind = ToolDiffLineKind.Skip, text = ""))
  }
  return preview
}

/** Compute a line diff between two snippets (no file line numbers available). */
internal fun computeToolLineDiff(
  oldText: String,
  newText: String,
  compactUnchanged: Boolean = false,
): ToolDiffPreview {
  val allOldLines = splitDiffLines(oldText)
  val allNewLines = splitDiffLines(newText)
  val inputTruncated = allOldLines.size > MAX_DIFF_INPUT_LINES || allNewLines.size > MAX_DIFF_INPUT_LINES
  val inputsEqual =
    allOldLines.size == allNewLines.size && allOldLines.indices.all { allOldLines[it] == allNewLines[it] }
  val comparisonTruncated = inputTruncated && !inputsEqual
  val oldLines = allOldLines.take(MAX_DIFF_INPUT_LINES)
  val newLines = allNewLines.take(MAX_DIFF_INPUT_LINES)
  val n = oldLines.size
  val m = newLines.size
  // lcs[i][j] = LCS length of oldLines[i..] vs newLines[j..]
  val lcs = Array(n + 1) { IntArray(m + 1) }
  for (i in n - 1 downTo 0) {
    for (j in m - 1 downTo 0) {
      lcs[i][j] =
        if (oldLines[i] == newLines[j]) {
          lcs[i + 1][j + 1] + 1
        } else {
          maxOf(lcs[i + 1][j], lcs[i][j + 1])
        }
    }
  }
  val lines = ArrayList<ToolDiffLine>(n + m)
  var i = 0
  var j = 0
  while (i < n && j < m) {
    when {
      oldLines[i] == newLines[j] -> {
        lines.add(ToolDiffLine(kind = ToolDiffLineKind.Context, text = oldLines[i]))
        i++
        j++
      }
      lcs[i + 1][j] >= lcs[i][j + 1] -> {
        lines.add(ToolDiffLine(kind = ToolDiffLineKind.Delete, text = oldLines[i]))
        i++
      }
      else -> {
        lines.add(ToolDiffLine(kind = ToolDiffLineKind.Add, text = newLines[j]))
        j++
      }
    }
  }
  while (i < n) {
    lines.add(ToolDiffLine(kind = ToolDiffLineKind.Delete, text = oldLines[i]))
    i++
  }
  while (j < m) {
    lines.add(ToolDiffLine(kind = ToolDiffLineKind.Add, text = newLines[j]))
    j++
  }
  val (added, removed) = diffStat(lines)
  val preview = compactLineDiff(lines, comparisonTruncated, compactUnchanged)
  return ToolDiffPreview(lines = preview, added = added, removed = removed, truncated = comparisonTruncated)
}

/** All-added preview for freshly written files, numbered from line 1. */
internal fun buildWriteDiffLines(
  content: String,
  maxLines: Int = 80,
): List<ToolDiffLine> {
  val sourceLines = splitDiffLines(content)
  val lines = ArrayList<ToolDiffLine>(minOf(sourceLines.size, maxLines) + 1)
  sourceLines.take(maxLines).forEachIndexed { index, text ->
    lines.add(ToolDiffLine(kind = ToolDiffLineKind.Add, text = text, lineNo = index + 1))
  }
  if (sourceLines.size > maxLines) {
    lines.add(ToolDiffLine(kind = ToolDiffLineKind.Skip, text = ""))
  }
  return lines
}

/** Concatenate per-edit diffs with skip separators (multi-edit calls). */
private fun joinDiffSections(
  sections: List<LocalDiff>,
  truncated: Boolean,
  maxLines: Int = MAX_DIFF_RENDER_LINES,
): ToolDiffPreview {
  val joined = ArrayList<ToolDiffLine>()
  val comparisonTruncated = truncated || sections.any { !it.complete }
  var previewTruncated = comparisonTruncated
  var added = 0
  var removed = 0
  for (section in sections) {
    if (section.complete) {
      added += section.added
      removed += section.removed
    }
    if (section.lines.isEmpty()) continue
    if (joined.isNotEmpty()) {
      if (joined.size >= maxLines) {
        previewTruncated = true
        break
      }
      joined.add(ToolDiffLine(kind = ToolDiffLineKind.Skip, text = ""))
    }
    val remaining = maxLines - joined.size
    if (section.lines.size > remaining) {
      for (index in 0 until remaining) joined.add(section.lines[index])
      previewTruncated = true
      break
    }
    joined.addAll(section.lines)
  }
  if (previewTruncated && joined.lastOrNull()?.kind != ToolDiffLineKind.Skip) {
    joined.add(ToolDiffLine(kind = ToolDiffLineKind.Skip, text = ""))
  }
  return ToolDiffPreview(lines = joined, added = added, removed = removed, truncated = comparisonTruncated)
}

private data class EditPair(
  val oldText: String,
  val newText: String,
)

private fun readFirst(
  record: JsonObject,
  keys: List<String>,
): String? = keys.firstNotNullOfOrNull { record.stringOrNull(it) }

private fun readEditPairs(args: JsonObject): Pair<List<EditPair>, Boolean> {
  val pairs = ArrayList<EditPair>()
  var inputChars = 0
  var truncated = false
  fun push(
    oldText: String?,
    newText: String?,
  ) {
    if (oldText == null || newText == null) return
    val pairChars = oldText.length + newText.length
    if (inputChars + pairChars > MAX_LOCAL_DIFF_INPUT_CHARS) {
      truncated = true
      return
    }
    inputChars += pairChars
    pairs.add(EditPair(oldText = oldText, newText = newText))
  }
  val edits = args["edits"].asArrayOrNull()
  if (edits != null) {
    edits.take(MAX_LOCAL_DIFF_PAIRS).forEach { entry ->
      val record = entry.asObjectOrNull() ?: return@forEach
      push(readFirst(record, OLD_TEXT_KEYS), readFirst(record, NEW_TEXT_KEYS))
    }
    if (edits.size > MAX_LOCAL_DIFF_PAIRS) truncated = true
  } else {
    push(readFirst(args, OLD_TEXT_KEYS), readFirst(args, NEW_TEXT_KEYS))
  }
  return pairs to truncated
}

private fun resolveEditDiff(args: JsonObject): ToolDiffPreview? {
  val (pairs, truncated) = readEditPairs(args)
  if (pairs.isEmpty()) return null
  val sections =
    pairs.map { pair ->
      val preview = computeToolLineDiff(pair.oldText, pair.newText)
      LocalDiff(lines = preview.lines, added = preview.added, removed = preview.removed, complete = !preview.truncated)
    }
  val result = joinDiffSections(sections, truncated)
  return result.takeIf { it.lines.isNotEmpty() }
}

/** Adapter over [parseUnifiedPatch] for `apply_patch`-style tool arguments. */
private fun resolvePatchDiff(args: JsonObject): ToolDiffPreview? {
  val patch =
    args.stringOrNull("patch")
      ?: args.stringOrNull("input")
      ?: args.stringOrNull("diff")
      ?: return null
  val lines = ArrayList<ToolDiffLine>()
  for (line in parseUnifiedPatch(patch)) {
    val kind =
      when (line.kind) {
        DiffLineKind.Add -> ToolDiffLineKind.Add
        DiffLineKind.Remove -> ToolDiffLineKind.Delete
        DiffLineKind.Context -> ToolDiffLineKind.Context
        DiffLineKind.Hunk -> ToolDiffLineKind.Skip
      }
    lines.add(ToolDiffLine(kind = kind, text = line.text, lineNo = line.newNumber ?: line.oldNumber))
  }
  if (!lines.any { it.kind == ToolDiffLineKind.Add || it.kind == ToolDiffLineKind.Delete }) return null
  val (added, removed) = diffStat(lines)
  return ToolDiffPreview(lines = lines, added = added, removed = removed, truncated = false)
}

/** Target path shown above an inline diff, using the same arg spellings as the web client. */
internal fun resolveToolCallPath(args: JsonObject?): String? {
  if (args == null) return null
  return PATH_KEYS.firstNotNullOfOrNull { args.stringOrNull(it) }
}

/**
 * Resolves inline diff rows for a tool call from its arguments. The gateway does not forward
 * producer `details` to the Android client, so precomputed display diffs are unavailable and
 * every row is computed locally.
 */
internal fun resolveToolCallDiff(
  name: String,
  args: JsonObject?,
  detailsDiff: String? = null,
): ToolDiffPreview? {
  if (args == null) return null
  detailsDiff?.let { parseDiffDetailsString(it) }?.let { return it }
  val key = name.trim().lowercase()
  return when {
    key in PATCH_TOOL_NAMES -> resolvePatchDiff(args)
    key in TEXT_EDITOR_TOOL_NAMES ->
      when (args.stringOrNull("command")?.lowercase()) {
        "create" -> {
          val content = args.stringOrNull("file_text") ?: args.stringOrNull("content") ?: return null
          val lines = buildWriteDiffLines(content)
          ToolDiffPreview(lines = lines, added = countTextLines(content), removed = 0)
        }
        "str_replace", "undo_edit" -> resolveEditDiff(args)
        "insert" -> {
          val insertText = args.stringOrNull("insert_text") ?: return null
          val preview = computeToolLineDiff("", insertText)
          preview.takeIf { it.lines.isNotEmpty() }
        }
        else -> null
      }
    key in EDIT_TOOL_NAMES -> resolveEditDiff(args)
    key in WRITE_TOOL_NAMES -> {
      val content = args.stringOrNull("content") ?: return null
      val lines = buildWriteDiffLines(content)
      ToolDiffPreview(lines = lines, added = countTextLines(content), removed = 0)
    }
    else -> null
  }
}
