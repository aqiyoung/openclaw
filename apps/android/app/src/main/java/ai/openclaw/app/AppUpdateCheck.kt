package ai.openclaw.app

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 从 GitHub Releases API 检查 OpenClaw Android 是否有新版本可更新。
 *
 * 对齐 sanyelive / FeiNiuMusic 共用的 app_update_core 引擎:
 *   - 代理优先 (gh-proxy.com), 直连兜底; 国内 / 移动宽带直连 api.github.com 会被墙,
 *     代理是这些用户唯一可达的路径。
 *   - 每条路径都做响应校验: 非 200 / 拿到 HTML / 解析失败都静默跳过试下一条,
 *     不会因代理偶尔抽风而整体失败。
 *   - 全部路径失败才返回 error (UI 显示"检查更新失败")。
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

  // 代理前缀链: gh-proxy.com 优先, 空串 = 直连兜底 (对齐 sanyelive app_update_core).
  private val PROXY_PREFIXES = listOf("https://gh-proxy.com/", "")

  private const val LATEST_RELEASE_API =
    "https://api.github.com/repos/aqiyoung/openclaw/releases/latest"

  private val client = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .followRedirects(true)
    .build()

  private val json = Json { ignoreUnknownKeys = true }

  /**
   * 检查是否有新版本。
   *
   * 依次尝试代理链 (gh-proxy.com 优先, 直连兜底)。每条路径都做响应校验,
   * 非 200 / HTML / 解析失败都静默跳过试下一条。全部失败才返回 error
   * (UI 显示"检查更新失败")。
   */
  suspend fun checkLatest(currentVersion: String): AppUpdateInfo {
    val failures = mutableListOf<String>()
    for (prefix in PROXY_PREFIXES) {
      val url = if (prefix.isEmpty()) LATEST_RELEASE_API else "$prefix$LATEST_RELEASE_API"
      try {
        val request = Request.Builder()
          .url(url)
          .header("Accept", "application/vnd.github.v3+json")
          .header("User-Agent", "OpenClaw-Android")
          .build()
        val body = client.newCall(request).execute().use { resp ->
          if (!resp.isSuccessful) {
            failures.add("api $url → HTTP ${resp.code}")
            null
          } else {
            resp.body.string()
          }
        } ?: continue
        val info = parseRelease(body, currentVersion)
        if (info != null) return info
        failures.add("api $url → 解析失败/无 tag")
      } catch (e: Exception) {
        failures.add("api $url → ${e.message?.take(60) ?: "异常"}")
      }
    }
    return AppUpdateInfo(
      latestVersion = currentVersion, hasUpdate = false,
      releaseName = null, releaseUrl = null, releaseNotes = null,
      isCritical = false, error = "检查失败",
    )
  }

  /** 解析 GitHub API 返回的 release 信息; 拿到 HTML / 非法 JSON 返回 null */
  private fun parseRelease(body: String, currentVersion: String): AppUpdateInfo? {
    val trimmed = body.trim()
    if (trimmed.isEmpty() || trimmed.startsWith("<")) return null
    return try {
      val release = json.decodeFromString<GitHubRelease>(trimmed)
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
}