package ai.openclaw.app.ui.chat

import ai.openclaw.app.chat.ChatMessageContent
import ai.openclaw.app.chat.ChatOutboxItem
import ai.openclaw.app.chat.ChatOutboxStatus
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class ChatMessageViewsTest {
  @Test
  fun transcriptBubblesExposeSpeakerWithoutReplacingMessageText() {
    val messages =
      listOf(
        Triple("user", "user body", false),
        Triple("assistant", "assistant body", false),
        Triple("system", "system body", false),
        Triple("assistant", "live body", true),
      )

    composeRule.setContent {
      Column {
        messages.forEachIndexed { index, (role, body, live) ->
          ChatBubble(
            messageId = "message-$index",
            entryId = if (role == "user") "entry-$index" else null,
            role = role,
            live = live,
            content = listOf(ChatMessageContent(type = "text", text = body)),
            timestampMs = null,
            onReplyMessage = {},
            sessionActionsEnabled = true,
            onRewindMessage = {},
            onForkMessage = {},
            speechState = null,
            onToggleListen = { _, _ -> },
            inlineMediaPlaybackBlocked = false,
            inlineWidgetResolverReady = false,
            resolveInlineWidgetResource = { _, _ -> null },
            loadImageArtifact = { null },
            loadMediaArtifact = { _, _, _ -> null },
          )
        }
      }
    }

    val userBubble = composeRule.onNode(hasContentDescription("You") and hasText("user body")).assertExists()
    val assistantBubble = composeRule.onNode(hasContentDescription("OpenClaw") and hasText("assistant body")).assertExists()
    composeRule.onNode(hasContentDescription("System") and hasText("system body")).assertExists()
    composeRule.onNode(hasContentDescription("OpenClaw") and hasText("live body")).assertExists()
    listOf(userBubble, assistantBubble).forEach { bubble ->
      val semantics = bubble.fetchSemanticsNode().config
      assertTrue(semantics.isMergingSemanticsOfDescendants)
      assertTrue(SemanticsActions.OnLongClick in semantics)
    }
    composeRule.onAllNodesWithText("You", useUnmergedTree = true).assertCountEquals(0)
    composeRule.onAllNodesWithText("OpenClaw", useUnmergedTree = true).assertCountEquals(0)
    composeRule.onAllNodesWithText("System", useUnmergedTree = true).assertCountEquals(1)
    composeRule.onAllNodesWithText("OpenClaw · Live", useUnmergedTree = true).assertCountEquals(1)

    userBubble.performSemanticsAction(SemanticsActions.OnLongClick) { action -> action() }
    listOf("Select text", "Reply", "Rewind to here", "Fork from here").forEach { label ->
      composeRule.onNode(hasText(label) and hasClickAction()).assertExists()
    }
    composeRule.onNodeWithText("Select text").performClick()
    composeRule.onAllNodesWithText("user body").assertCountEquals(2)
    composeRule.onNode(hasText("Done") and hasClickAction()).performClick()

    assistantBubble.performSemanticsAction(SemanticsActions.OnLongClick) { action -> action() }
    composeRule.onNode(hasText("Listen") and hasClickAction()).assertExists()
    composeRule.onNode(hasText("Reply") and hasClickAction()).assertExists()
  }

  @Test
  fun outboxBubbleExposesSpeakerWithoutReplacingStatusOrActions() {
    composeRule.setContent {
      Column {
        ChatOutboxBubble(
          item =
            ChatOutboxItem(
              id = "outbox-1",
              sessionKey = "main",
              text = "queued body",
              thinkingLevel = "low",
              createdAtMs = 0L,
              status = ChatOutboxStatus.Queued,
              retryCount = 0,
              lastError = null,
              ownerAgentId = "main",
            ),
          onRetry = {},
          onDelete = {},
        )
        ChatBubble(
          messageId = "audio-message",
          entryId = null,
          role = "assistant",
          live = false,
          content =
            listOf(
              ChatMessageContent(
                type = "audio",
                mimeType = "audio/mpeg",
                fileName = "voice-note.mp3",
                artifactId = "audio-artifact",
              ),
            ),
          timestampMs = null,
          onReplyMessage = {},
          sessionActionsEnabled = false,
          onRewindMessage = {},
          onForkMessage = {},
          speechState = null,
          onToggleListen = { _, _ -> },
          inlineMediaPlaybackBlocked = false,
          inlineWidgetResolverReady = false,
          resolveInlineWidgetResource = { _, _ -> null },
          loadImageArtifact = { null },
          loadMediaArtifact = { _, _, _ -> null },
        )
      }
    }

    composeRule
      .onNode(
        hasContentDescription("You") and
          hasText("queued body") and
          hasAnyDescendant(hasText("Delete") and hasClickAction()),
      ).assertExists()
    composeRule
      .onNode(
        hasContentDescription("OpenClaw") and
          hasAnyDescendant(hasContentDescription("Play audio") and hasClickAction()),
      ).assertExists()
  }

  @Test
  fun managedImageCompositionRequestsItsArtifact() {
    val artifactId = "artifact_managed_image_11111111-1111-4111-8111-111111111111"
    val requested = mutableListOf<String>()
    val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()

    try {
      controller.get().setContent {
        ChatBubble(
          messageId = "managed-image",
          entryId = null,
          role = "assistant",
          live = false,
          content =
            listOf(
              ChatMessageContent(
                type = "image",
                mimeType = "image/png",
                artifactId = artifactId,
                alt = "Managed image",
              ),
            ),
          timestampMs = null,
          onReplyMessage = {},
          sessionActionsEnabled = false,
          onRewindMessage = {},
          onForkMessage = {},
          speechState = null,
          onToggleListen = { _, _ -> },
          inlineMediaPlaybackBlocked = false,
          inlineWidgetResolverReady = true,
          resolveInlineWidgetResource = { _, _ -> null },
          loadImageArtifact = { requestedArtifactId ->
            requested += requestedArtifactId
            null
          },
          loadMediaArtifact = { _, _, _ -> null },
        )
      }
      shadowOf(Looper.getMainLooper()).idle()

      assertEquals(listOf(artifactId), requested)
    } finally {
      controller.pause().stop().destroy()
      shadowOf(Looper.getMainLooper()).idle()
    }
  }
}
