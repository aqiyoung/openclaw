package ai.openclaw.app

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 检查 OpenClaw Android 是否有新版本可更新。
 *
 * 采用双轨检查策略（2026-08-11 修复）:
 *
 * 1. **主路径 — jsDelivr CDN** (静态文件, 国内节点, 无速率限制):
 *    https://cdn.jsdelivr.net/gh/aqiyoung/openclaw@main/apps/android/version.json
 *    返回 { "version": "2026.8.12", "versionCode": 2026081201 }
 *
 * 2. **兜底 — GitHub Releases API** (经代理链尝试):
 *    依次尝试各代理前缀 + 直连。每条都校验响应 (非200/HTML/解析失败静默跳过)。
 *    全部失败才返回 error。
 *
 * 为什么不用纯 GitHub API:
 *   - api.github.com 在国内被墙或严重限速 (60次/h 未认证)
 *   - 常见 GitHub 代理 (gh-proxy.com / ghfast.top 等) 频繁失效/关停
 *   - jsDelivr 是 npm/GitHub 官方 CDN, 稳定性远高于个人代理服务
 */

@Serializable
data class VersionJson(
  val version: String = "",
  val versionCode: Int = 0,
)

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

  /** fork 的 Release 页面 */
  const val RELEASE_PAGE_URL = "https://github.com/aqiyoung/openclaw/releases"

  /** 主路径: jsDelivr CDN 托管的 version.json */
  private const val JSDELIVR_VERSION_URL =
    "https://cdn.jsdelivr.net/gh/aqiyoung/openclaw@main/apps/android/version.json"

  /** 兜底: GitHub Releases API 原始地址 */
  private const val LATEST_RELEASE_API =
    "https://api.github.com/repos/aqiyoung/openclaw/releases/latest"

  /** GitHub API 代理前缀链 (按优先级排列; 当前大多已失效, 保留以备恢复) */
  private val API_PROXY_PREFIXES = listOf(
    "https://mirror.ghproxy.com/",
    "https://gh-proxy.com/",
    "", // 直连 (最后手段)
  )

  private val client = OkHttpClient.Builder()
    .connectTimeout(12, TimeUnit.SECONDS)
    .readTimeout(12, TimeUnit.SECONDS)
    .followRedirects(true)
    .build()

  private val json = Json { ignoreUnknownKeys = true }

  /**
   * 检查是否有新版本。
   *
   * 先走 jsDelivr CDN (快、稳、国内可达); 失败再逐条试 GitHub API 代理链;
   * 全部失败才返回 error。
   */
  suspend fun checkLatest(currentVersion: String): AppUpdateInfo {
    // ── 路径 1: jsDelivr CDN (主) ──
    try {
      val info = checkViaJsDelivr(currentVersion)
      if (info != null) return info
    } catch (e: Exception) {
      // jsDelivr 失败, 继续走 API 兜底
    }

    // ── 路径 2: GitHub API 代理链 (兜底) ──
    return tryCheckViaApi(currentVersion)
  }

  // ──── 路径 1: jsDelivr CDN ────

  private fun checkViaJsDelivr(currentVersion: String): AppUpdateInfo? {
    val request = Request.Builder()
      .url(JSDELIVR_VERSION_URL)
      .header("User-Agent", "OpenClaw-Android")
      .build()

    val body = client.newCall(request).execute().use { resp ->
      if (!resp.isSuccessful) return null
      resp.body.string()
    }

    val trimmed = body.trim()
    if (trimmed.isEmpty() || trimmed.startsWith("<")) return null

    val vj = try { json.decodeFromString<VersionJson>(trimmed) } catch (_: Exception) { return null }
    val latestName = vj.version.trim().removePrefix("v").removePrefix("V")
    if (latestName.isEmpty()) return null

    val hasUpdate = compareVersions(latestName, currentVersion) > 0
    return AppUpdateInfo(
      latestVersion = latestName,
      hasUpdate = hasUpdate,
      releaseName = "v$latestName",
      releaseUrl = RELEASE_PAGE_URL,
      releaseNotes = null, // jsDelivr 不提供 release notes
      isCritical = false,
    )
  }

  // ──── 路径 2: GitHub API 代理链 (兜底) ────

  private fun tryCheckViaApi(currentVersion: String): AppUpdateInfo {
    for (prefix in API_PROXY_PREFIXES) {
      val url = if (prefix.isEmpty()) LATEST_RELEASE_API else "$prefix$LATEST_RELEASE_API"
      try {
        val request = Request.Builder()
          .url(url)
          .header("Accept", "application/vnd.github.v3+json")
          .header("User-Agent", "OpenClaw-Android")
          .build()
        val body = client.newCall(request).execute().use { resp ->
          if (!resp.isSuccessful) continue
          resp.body.string()
        }
        val info = parseRelease(body, currentVersion)
        if (info != null) return info
      } catch (_: Exception) {
        // 静默跳过, 试下一条
      }
    }
    // 全部失败
    return AppUpdateInfo(
      latestVersion = currentVersion, hasUpdate = false,
      releaseName = null, releaseUrl = null, releaseNotes = null,
      isCritical = false, error = "无法连接更新服务，请检查网络或手动打开 GitHub 发布页面。",
    )
  }

  /** 解析 GitHub API 返回的 release 信息; HTML / 非法 JSON 返回 null */
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

  // ──── 工具方法 ────

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
