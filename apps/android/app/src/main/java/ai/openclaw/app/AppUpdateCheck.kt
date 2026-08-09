package ai.openclaw.app

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 从 GitHub Releases API 检查 OpenClaw Android 是否有新版本可更新。
 *
 * 参考 FeiNiuMusic AppUpdateService 实现。
 */
@Serializable
data class GitHubRelease(
  val tag_name: String = "",
  val name: String? = null,
  val body: String? = null,
  val html_url: String? = null,
)

data class AppUpdateInfo(
  val latestVersion: String,
  val hasUpdate: Boolean,
  val releaseName: String?,
  val releaseUrl: String?,
  val releaseNotes: String?,
  val isCritical: Boolean,
  val error: String? = null,
)

object AppUpdateCheck {

  // 指向我们 fork 的 release 页面
  const val RELEASE_PAGE_URL = "https://github.com/aqiyoung/openclaw/releases"

  private const val LATEST_RELEASE_API =
    "https://api.github.com/repos/aqiyoung/openclaw/releases/latest"

  private val client = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .followRedirects(true)
    .build()

  private val json = Json { ignoreUnknownKeys = true }

  /**
   * 检查是否有新版本。出错时返回 hasUpdate = false。
   */
  suspend fun checkLatest(currentVersion: String): AppUpdateInfo {
    return try {
      checkLatestImpl(currentVersion) ?: tryFallback(currentVersion)
    } catch (e: Exception) {
      AppUpdateInfo(
        latestVersion = currentVersion, hasUpdate = false,
        releaseName = null, releaseUrl = null, releaseNotes = null,
        isCritical = false, error = e.message?.take(80) ?: "检查失败",
      )
    }
  }

  /** 直连 GitHub API 检查 */
  private suspend fun checkLatestImpl(currentVersion: String): AppUpdateInfo? {
    return try {
      val request = Request.Builder()
        .url(LATEST_RELEASE_API)
        .header("Accept", "application/vnd.github.v3+json")
        .header("User-Agent", "OpenClaw-Android")
        .build()

      val response = client.newCall(request).execute()
      if (!response.isSuccessful) return null
      val body = response.body.string()
      return parseRelease(body, currentVersion)
    } catch (_: Exception) {
      null
    }
  }

  /** 直连失败时走 gh-proxy 镜像兜底 */
  private suspend fun tryFallback(currentVersion: String): AppUpdateInfo {
    val proxyUrl = "https://gh-proxy.com/https://api.github.com/repos/aqiyoung/openclaw/releases/latest"
    val request = Request.Builder()
      .url(proxyUrl)
      .header("Accept", "application/vnd.github.v3+json")
      .header("User-Agent", "OpenClaw-Android")
      .build()

    val response = client.newCall(request).execute()
    val body = response.body.string()
    return parseRelease(body, currentVersion) ?: AppUpdateInfo(
      latestVersion = currentVersion, hasUpdate = false,
      releaseName = null, releaseUrl = null, releaseNotes = null,
      isCritical = false, error = "解析失败",
    )
  }

  /** 解析 GitHub API 返回的 release 信息 */
  private fun parseRelease(body: String, currentVersion: String): AppUpdateInfo? {
    return try {
      val release = json.decodeFromString<GitHubRelease>(body)
      val tagName = release.tag_name.trim()
      val latestName = tagName.removePrefix("v").removePrefix("V")
      val hasUpdate = compareVersions(latestName, currentVersion) > 0

      AppUpdateInfo(
        latestVersion = latestName,
        hasUpdate = hasUpdate,
        releaseName = release.name,
        releaseUrl = release.html_url ?: RELEASE_PAGE_URL,
        releaseNotes = release.body,
        isCritical = isCriticalRelease(release.body),
      )
    } catch (_: Exception) {
      null
    }
  }

  /** 比较版本号，a > b 返回正数 */
  private fun compareVersions(a: String, b: String): Int {
    fun parse(v: String): List<Int> {
      val s = v.trim().removePrefix("v").removePrefix("V")
      val cut = s.indexOfFirst { it == '+' || it == '-' }
      val clean = if (cut >= 0) s.substring(0, cut) else s
      return clean.split(".").map { it.toIntOrNull() ?: 0 }
    }
    val left = parse(a)
    val right = parse(b)
    val maxLen = maxOf(left.size, right.size)
    for (i in 0 until maxLen) {
      val l = left.getOrElse(i) { 0 }
      val r = right.getOrElse(i) { 0 }
      if (l != r) return l.compareTo(r)
    }
    return 0
  }

  /** release body 首非空行含 **P0** 或 **critical** 视为重要更新 */
  private fun isCriticalRelease(body: String?): Boolean {
    val firstLine = body?.lineSequence()?.firstOrNull { it.isNotBlank() } ?: return false
    val lower = firstLine.lowercase()
    return lower.contains("**p0**") || lower.contains("**critical**")
  }

  private fun noUpdate(currentVersion: String) = AppUpdateInfo(
    latestVersion = currentVersion,
    hasUpdate = false,
    releaseName = null,
    releaseUrl = null,
    releaseNotes = null,
    isCritical = false,
  )
}