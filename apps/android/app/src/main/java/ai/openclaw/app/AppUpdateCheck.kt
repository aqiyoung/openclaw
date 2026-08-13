package ai.openclaw.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * OpenClaw Android 更新检查 —— 与 sanyelive / FeiNiuMusic 统一的引擎.
 *
 * 可达路径 (对齐三仓共用的 app_update_core.dart):
 *   1) GitHub API 经 gh-proxy.com 代理 (国内/移动宽带直连 api.github.com 被墙, 代理是唯一可达路径)
 *   2) GitHub API 直连 (兜底, 覆盖 VPN/海外)
 *   3) jsDelivr @meta 分支 version.json (最后防线, 国内 CDN, 经 gh-proxy 包裹 + cache-buster)
 *
 * 任一层 403/超时/拿到 HTML 都静默跳过试下一层, 不会因代理偶尔抽风而整体失败.
 * 全部失败才返回 error (UI 显示"检查更新失败"), 并把每层失败原因带回便于真机诊断.
 *
 * ⚠️ 所有网络调用必须在 IO 线程执行 (Dispatchers.IO), 否则 Android 抛 NetworkOnMainThreadException.
 *   Compose 的 rememberCoroutineScope().launch{} 默认跑在 Main 线程,
 *   所以 checkLatest 内部用 withContext(Dispatchers.IO) 包裹全部 HTTP 操作.
 */
@Serializable
data class GitHubRelease(
  val tag_name: String = "",
  val name: String? = null,
  val body: String? = null,
  val html_url: String? = null,
)

@Serializable
data class MetaVersion(
  val tag: String = "",
  val versionName: String? = null,
  val versionCode: Int = 0,
  val releaseName: String? = null,
  val apk: Map<String, String>? = null,
  val releaseUrl: String? = null,
  val critical: Boolean = false,
  val notes: String? = null,
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

  /** 代理前缀链: gh-proxy.com 优先, 空串 = 直连兜底 (对齐 sanyelive app_update_core). */
  private val PROXY_PREFIXES = listOf("https://gh-proxy.com/", "")

  private const val LATEST_RELEASE_API =
    "https://api.github.com/repos/aqiyoung/openclaw/releases/latest"

  /** 最后防线: jsDelivr @meta 分支托管的 version.json (国内 CDN, 无需认证). */
  private const val META_URL =
    "https://cdn.jsdelivr.net/gh/aqiyoung/openclaw@meta/version.json"

  private val client = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .followRedirects(true)
    .build()

  private val json = Json { ignoreUnknownKeys = true }

  /**
   * 检查是否有新版本。
   * 可达路径: GitHub API(gh-proxy→直连) → jsDelivr @meta(gh-proxy→直连, cache-buster)。
   * 任一层成功即返回; 全部失败返回 error 并把每层失败原因带回 (便于真机诊断)。
   *
   * ⚠️ 全部网络操作在 Dispatchers.IO 执行, 避免 NetworkOnMainThreadException
   *     (Compose 默认 scope.launch 跑在 Main 线程, OkHttp 同步 execute() 必须切线程).
   */
  suspend fun checkLatest(currentVersion: String): AppUpdateInfo = withContext(Dispatchers.IO) {
    val failures = mutableListOf<String>()

    // ── 1) GitHub API: 代理链 (gh-proxy 优先, 直连兜底) ──
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
            failures.add("API $url → HTTP ${resp.code}")
            return@use null
          }
          resp.body.string()
        } ?: continue
        val info = parseRelease(body, currentVersion)
        if (info != null) return@withContext info
        failures.add("API $url → 解析失败 (无 tag_name / 非法 JSON)")
      } catch (e: Exception) {
        failures.add("API $url → ${e.message ?: e.javaClass.simpleName}")
      }
    }

    // ── 2) jsDelivr @meta 兜底 (国内 CDN, 经 gh-proxy 包裹 + cache-buster, 对齐 sanyelive) ──
    val cacheBuster = System.currentTimeMillis()
    for (prefix in PROXY_PREFIXES) {
      val url = "${prefix}${META_URL}?_t=$cacheBuster"
      try {
        val request = Request.Builder()
          .url(url)
          .header("User-Agent", "OpenClaw-Android")
          .header("Accept", "application/json")
          .build()
        val body = client.newCall(request).execute().use { resp ->
          if (!resp.isSuccessful) {
            failures.add("META $url → HTTP ${resp.code}")
            return@use null
          }
          resp.body.string()
        } ?: continue
        val info = parseMeta(body, currentVersion)
        if (info != null) return@withContext info
        failures.add("META $url → 解析失败 (无 tag / 非法 JSON)")
      } catch (e: Exception) {
        failures.add("META $url → ${e.message ?: e.javaClass.simpleName}")
      }
    }

    // 全部失败: 把每层失败原因带出来, 便于真机诊断 (对齐 sanyelive failures 列表).
    val detail = if (failures.isNotEmpty()) "\n" + failures.joinToString("\n") else ""
    AppUpdateInfo(
      latestVersion = currentVersion, hasUpdate = false,
      releaseName = null, releaseUrl = null, releaseNotes = null,
      isCritical = false,
      error = "无法连接更新服务，请检查网络或手动打开 GitHub 发布页面。${detail}",
    )
  }

  /** 解析 GitHub API 返回的 release 信息; HTML / 非法 JSON 返回 null */
  private fun parseRelease(body: String, currentVersion: String): AppUpdateInfo? {
    val trimmed = body.trim()
    if (trimmed.isEmpty() || trimmed.startsWith("<")) return null
    return try {
      val release = json.decodeFromString<GitHubRelease>(trimmed)
      val tagName = release.tag_name.trim()
      if (tagName.isEmpty()) return null
      val latestName = tagName.removePrefix("v").removePrefix("V")
      val hasUpdate = compareVersions(latestName, currentVersion) > 0
      AppUpdateInfo(
        latestVersion = latestName,
        hasUpdate = hasUpdate,
        releaseName = release.name ?: tagName,
        releaseUrl = release.html_url ?: RELEASE_PAGE_URL,
        releaseNotes = release.body,
        isCritical = isCriticalRelease(release.body),
      )
    } catch (_: Exception) {
      null
    }
  }

  /** 解析 jsDelivr @meta version.json; HTML / 非法 JSON 返回 null */
  private fun parseMeta(body: String, currentVersion: String): AppUpdateInfo? {
    val trimmed = body.trim()
    if (trimmed.isEmpty() || trimmed.startsWith("<")) return null
    return try {
      val meta = json.decodeFromString<MetaVersion>(trimmed)
      val tag = meta.tag.trim()
      if (tag.isEmpty()) return null
      val latestName = tag.removePrefix("v").removePrefix("V")
      val hasUpdate = compareVersions(latestName, currentVersion) > 0
      AppUpdateInfo(
        latestVersion = latestName,
        hasUpdate = hasUpdate,
        releaseName = meta.releaseName ?: tag,
        releaseUrl = meta.releaseUrl ?: RELEASE_PAGE_URL,
        releaseNotes = meta.notes,
        isCritical = meta.critical,
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
