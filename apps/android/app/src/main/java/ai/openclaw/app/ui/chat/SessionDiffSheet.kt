package ai.openclaw.app.ui.chat

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.chat.DiffLine
import ai.openclaw.app.chat.DiffLineKind
import ai.openclaw.app.chat.SessionDiffFile
import ai.openclaw.app.chat.SessionDiffFileStatus
import ai.openclaw.app.chat.SessionsDiffResult
import ai.openclaw.app.chat.parseUnifiedPatch
import ai.openclaw.app.i18n.nativeString
import ai.openclaw.app.ui.design.ClawTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SessionDiffSheet(
  viewModel: MainViewModel,
  sessionKey: String,
  agentId: String,
  onDismiss: () -> Unit,
) {
  var result by remember(sessionKey) { mutableStateOf<SessionsDiffResult?>(null) }
  var selectedPath by remember(sessionKey) { mutableStateOf<String?>(null) }
  var loading by remember(sessionKey) { mutableStateOf(true) }
  var error by remember(sessionKey) { mutableStateOf<String?>(null) }
  val scope = rememberCoroutineScope()

  suspend fun load() {
    loading = true
    error = null
    runCatching { viewModel.loadSessionDiff(sessionKey, agentId) }
      .onSuccess { result = it }
      .onFailure {
        if (it is CancellationException) throw it
        error = it.message ?: nativeString("Couldn’t load changes")
      }
    loading = false
  }

  LaunchedEffect(sessionKey) { load() }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
  ) {
    Column(modifier = Modifier.fillMaxSize().padding(bottom = 16.dp)) {
      val current = result
      val path = selectedPath
      when {
        path != null && current != null -> {
          val file = current.files.firstOrNull { it.path == path }
          if (file == null) {
            selectedPath = null
          } else {
            SessionDiffFileHeader(
              file = file,
              onBack = { selectedPath = null },
            )
            SessionDiffFileBody(file = file)
          }
        }
        loading -> SessionDiffCentered { CircularProgressIndicator() }
        error != null -> SessionDiffCentered { Text(text = error ?: "", color = ClawTheme.colors.textMuted) }
        current == null || current.files.isEmpty() ->
          SessionDiffCentered {
            Text(
              text =
                current?.unavailableReason?.takeIf { it.isNotBlank() }
                  ?: nativeString("No code changes yet"),
              color = ClawTheme.colors.textMuted,
            )
          }
        else -> {
          SessionDiffSummaryHeader(
            result = current,
            onRefresh = { scope.launch { load() } },
          )
          LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(current.files, key = { it.path }) { file ->
              SessionDiffFileRow(file = file, onClick = { selectedPath = file.path })
              HorizontalDivider(color = ClawTheme.colors.border)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SessionDiffCentered(content: @Composable () -> Unit) {
  Box(modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp), contentAlignment = Alignment.Center) {
    content()
  }
}

@Composable
private fun SessionDiffSummaryHeader(
  result: SessionsDiffResult,
  onRefresh: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = nativeString("Changed files"),
        style = MaterialTheme.typography.titleMedium,
        color = ClawTheme.colors.text,
      )
      val branch = result.branch?.takeIf { it.isNotBlank() }
      val base = result.baseRef?.takeIf { it.isNotBlank() }
      val subtitle =
        when {
          branch != null && base != null -> "$branch ↔ $base"
          branch != null -> branch
          base != null -> base
          else -> null
        }
      if (subtitle != null) {
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = ClawTheme.colors.textMuted,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
    Text(
      text = "+${result.additions} −${result.deletions}",
      style = MaterialTheme.typography.bodyMedium,
      color = ClawTheme.colors.textMuted,
      fontFamily = FontFamily.Monospace,
    )
    IconButton(onClick = onRefresh) {
      Icon(Icons.Filled.Refresh, contentDescription = nativeString("Refresh"), tint = ClawTheme.colors.textMuted)
    }
  }
  HorizontalDivider(color = ClawTheme.colors.border)
}

@Composable
private fun SessionDiffFileRow(
  file: SessionDiffFile,
  onClick: () -> Unit,
) {
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable(onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = sessionDiffStatusLabel(file.status),
      style = MaterialTheme.typography.labelLarge,
      color = sessionDiffStatusColor(file.status),
      fontFamily = FontFamily.Monospace,
      modifier = Modifier.padding(end = 12.dp),
    )
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = file.path,
        style = MaterialTheme.typography.bodyMedium,
        color = ClawTheme.colors.text,
        maxLines = 1,
        overflow = TextOverflow.MiddleEllipsis,
      )
      if (file.oldPath != null && file.oldPath != file.path) {
        Text(
          text = file.oldPath,
          style = MaterialTheme.typography.bodySmall,
          color = ClawTheme.colors.textSubtle,
          maxLines = 1,
          overflow = TextOverflow.MiddleEllipsis,
        )
      }
    }
    Text(
      text = "+${file.additions} −${file.deletions}",
      style = MaterialTheme.typography.bodySmall,
      color = ClawTheme.colors.textMuted,
      fontFamily = FontFamily.Monospace,
    )
  }
}

@Composable
private fun SessionDiffFileHeader(
  file: SessionDiffFile,
  onBack: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    IconButton(onClick = onBack) {
      Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = nativeString("Back"), tint = ClawTheme.colors.text)
    }
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = file.path,
        style = MaterialTheme.typography.titleSmall,
        color = ClawTheme.colors.text,
        maxLines = 1,
        overflow = TextOverflow.MiddleEllipsis,
      )
      Text(
        text = "+${file.additions} −${file.deletions}",
        style = MaterialTheme.typography.bodySmall,
        color = ClawTheme.colors.textMuted,
        fontFamily = FontFamily.Monospace,
      )
    }
  }
  HorizontalDivider(color = ClawTheme.colors.border)
}

@Composable
private fun SessionDiffFileBody(file: SessionDiffFile) {
  val rows = remember(file.path, file.patch) { parseUnifiedPatch(file.patch) }
  if (rows.isEmpty()) {
    SessionDiffCentered {
      Text(
        text =
          if (file.binary) {
            nativeString("Binary file")
          } else {
            file.patch?.let { nativeString("No preview available") } ?: nativeString("Binary file")
          },
        color = ClawTheme.colors.textMuted,
      )
    }
    return
  }
  Surface(color = ClawTheme.colors.codeBg) {
    SelectionContainer {
      Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        rows.forEach { line -> SessionDiffLineRow(line = line) }
      }
    }
  }
}

@Composable
private fun SessionDiffLineRow(line: DiffLine) {
  val colors = ClawTheme.colors
  val background =
    when (line.kind) {
      DiffLineKind.Add -> colors.successSoft
      DiffLineKind.Remove -> colors.dangerSoft
      DiffLineKind.Hunk -> colors.surfaceRaised
      DiffLineKind.Context -> Color.Transparent
    }
  val foreground =
    when (line.kind) {
      DiffLineKind.Hunk -> colors.textSubtle
      else -> colors.codeText
    }
  val gutter = line.oldNumber ?: line.newNumber
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .background(background)
        .padding(horizontal = 12.dp, vertical = 1.dp),
  ) {
    Text(
      text = gutter?.toString() ?: "",
      style = MaterialTheme.typography.bodySmall,
      fontFamily = FontFamily.Monospace,
      color = colors.textSubtle,
      modifier = Modifier.padding(end = 10.dp),
    )
    Text(
      text = line.text.ifEmpty { " " },
      style = MaterialTheme.typography.bodySmall,
      fontFamily = FontFamily.Monospace,
      color = foreground,
      softWrap = false,
    )
  }
}

@Composable
private fun sessionDiffStatusColor(status: SessionDiffFileStatus): Color =
  when (status) {
    SessionDiffFileStatus.Added -> ClawTheme.colors.success
    SessionDiffFileStatus.Deleted -> ClawTheme.colors.danger
    SessionDiffFileStatus.Renamed -> ClawTheme.colors.warning
    SessionDiffFileStatus.Modified -> ClawTheme.colors.accent
  }

@Composable
private fun sessionDiffStatusLabel(status: SessionDiffFileStatus): String =
  when (status) {
    SessionDiffFileStatus.Added -> "A"
    SessionDiffFileStatus.Modified -> "M"
    SessionDiffFileStatus.Deleted -> "D"
    SessionDiffFileStatus.Renamed -> "R"
  }
