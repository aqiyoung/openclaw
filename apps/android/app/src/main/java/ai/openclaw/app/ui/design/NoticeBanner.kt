package ai.openclaw.app.ui.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * iOS `OpenClawNoticeBanner` port — a ProCard-styled connection / runtime notice.
 *
 * Mirrors apps/ios/Sources/Design/OpenClawProComponents.swift: `OpenClawNoticeBanner`
 * (tinted icon badge, title + owner label, message, optional accent / request-ID detail,
 * and optional primary / secondary glass actions).
 */
sealed interface NoticeDetail {
  val value: String

  data class Accent(override val value: String) : NoticeDetail
  data class RequestId(override val value: String) : NoticeDetail
}

@Composable
internal fun NoticeBanner(
  icon: ImageVector,
  title: String,
  message: String,
  ownerLabel: String = "",
  tint: Color = ClawTheme.colors.warning,
  detail: NoticeDetail? = null,
  primaryActionTitle: String? = null,
  onPrimaryAction: (() -> Unit)? = null,
  secondaryActionTitle: String? = null,
  onSecondaryAction: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = ClawTheme.shapes.sheet,
    color = ClawTheme.colors.surfaceRaised,
    contentColor = ClawTheme.colors.text,
    border = BorderStroke(1.dp, ClawTheme.colors.border),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        NoticeIconBadge(icon = icon, tint = tint)
        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              text = title,
              style = ClawTheme.type.section,
              color = ClawTheme.colors.text,
              modifier = Modifier.weight(1f),
            )
            if (ownerLabel.isNotEmpty()) {
              Text(
                text = ownerLabel,
                style = ClawTheme.type.label,
                color = ClawTheme.colors.textMuted,
              )
            }
          }
          Text(
            text = message,
            style = ClawTheme.type.caption,
            color = ClawTheme.colors.textMuted,
          )
          detail?.let { noticeDetail ->
            when (noticeDetail) {
              is NoticeDetail.Accent ->
                Text(
                  text = noticeDetail.value,
                  style = ClawTheme.type.caption,
                  color = tint,
                )
              is NoticeDetail.RequestId ->
                Text(
                  text = "Request ID: ${noticeDetail.value}",
                  style = ClawTheme.type.caption,
                  color = ClawTheme.colors.textMuted,
                )
            }
          }
        }
      }
      if (primaryActionTitle != null || secondaryActionTitle != null) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          if (primaryActionTitle != null && onPrimaryAction != null) {
            ClawPrimaryButton(
              text = primaryActionTitle,
              onClick = onPrimaryAction,
              modifier = Modifier.weight(1f),
            )
          }
          if (secondaryActionTitle != null && onSecondaryAction != null) {
            ClawSecondaryButton(
              text = secondaryActionTitle,
              onClick = onSecondaryAction,
              modifier = Modifier.weight(1f),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun NoticeIconBadge(
  icon: ImageVector,
  tint: Color,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier.size(28.dp),
    shape = CircleShape,
    color = tint.copy(alpha = 0.14f),
    border = BorderStroke(1.dp, tint.copy(alpha = 0.35f)),
    contentColor = tint,
  ) {
    Box(contentAlignment = Alignment.Center) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(15.dp),
        tint = tint,
      )
    }
  }
}
