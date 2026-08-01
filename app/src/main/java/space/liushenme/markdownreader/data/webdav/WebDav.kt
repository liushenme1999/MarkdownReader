package space.liushenme.markdownreader.data.webdav

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileOutputStream
import java.io.StringReader
import java.net.URLDecoder
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * 精简 WebDAV 客户端（参考 Legado WebDav，去掉书源/Cronet 依赖）。
 */
class WebDav(
    val path: String,
    private val authorization: Authorization,
) {
    companion object {
        private const val DEFAULT_CONTENT_TYPE = "application/octet-stream"

        private val dateTimeFormatter = DateTimeFormatter.RFC_1123_DATE_TIME

        private const val PROP_FIND =
            """<?xml version="1.0"?>
            <a:propfind xmlns:a="DAV:">
                <a:prop>
                    <a:displayname/>
                    <a:resourcetype/>
                    <a:getcontentlength/>
                    <a:creationdate/>
                    <a:getlastmodified/>
                </a:prop>
            </a:propfind>"""

        private const val EXISTS =
            """<?xml version="1.0"?>
            <propfind xmlns="DAV:">
               <prop>
                  <resourcetype />
               </prop>
            </propfind>"""
    }

    private val httpUrl: String by lazy {
        path.replace("davs://", "https://").replace("dav://", "http://")
    }

    private val host: String? by lazy {
        runCatching { httpUrl.toHttpUrl().host }.getOrNull()
    }

    private val webDavClient: OkHttpClient by lazy {
        val authInterceptor = Interceptor { chain ->
            var request = chain.request()
            if (host != null && request.url.host.equals(host, ignoreCase = true)) {
                request = request.newBuilder()
                    .header(authorization.name, authorization.data)
                    .build()
            }
            chain.proceed(request)
        }
        OkHttpClient.Builder()
            .callTimeout(0, TimeUnit.SECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .writeTimeout(0, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor)
            .build()
    }

    @Throws(WebDavException::class)
    suspend fun listFiles(): List<WebDavFile> = withContext(Dispatchers.IO) {
        val body = propFindResponse(depth = 1) ?: return@withContext emptyList()
        parseBody(body).filter { !pathsEqual(it.path, path) }
    }

    suspend fun exists(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(httpUrl)
                .header("Depth", "0")
                .method("PROPFIND", EXISTS.toRequestBody("application/xml".toMediaType()))
                .build()
            webDavClient.newCall(request).execute().use { it.isSuccessful }
        }.onFailure {
            currentCoroutineContext().ensureActive()
        }.getOrDefault(false)
    }

    suspend fun check(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(httpUrl)
                .header("Depth", "0")
                .method("PROPFIND", EXISTS.toRequestBody("application/xml".toMediaType()))
                .build()
            webDavClient.newCall(request).execute().use { it.code != 401 }
        }.onFailure {
            currentCoroutineContext().ensureActive()
        }.getOrDefault(true)
    }

    suspend fun makeAsDir(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            if (!exists()) {
                val request = Request.Builder()
                    .url(httpUrl)
                    .method("MKCOL", null)
                    .build()
                webDavClient.newCall(request).execute().use { checkResult(it) }
            }
            true
        }.onFailure {
            currentCoroutineContext().ensureActive()
        }.getOrDefault(false)
    }

    @Throws(WebDavException::class)
    suspend fun downloadTo(savedPath: String, replaceExisting: Boolean) =
        withContext(Dispatchers.IO) {
            val file = File(savedPath)
            if (file.exists() && !replaceExisting) return@withContext
            file.parentFile?.mkdirs()
            downloadBytes().inputStream().use { input ->
                FileOutputStream(file).use { output -> input.copyTo(output) }
            }
        }

    @Throws(WebDavException::class)
    suspend fun downloadBytes(): ByteArray = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(httpUrl).get().build()
        webDavClient.newCall(request).execute().use { response ->
            checkResult(response)
            response.body?.bytes() ?: throw WebDavException("WebDav下载出错：空响应")
        }
    }

    @Throws(WebDavException::class)
    suspend fun upload(file: File, contentType: String = DEFAULT_CONTENT_TYPE) =
        withContext(Dispatchers.IO) {
            if (!file.exists()) throw WebDavException("文件不存在")
            val request = Request.Builder()
                .url(httpUrl)
                .put(file.asRequestBody(contentType.toMediaType()))
                .build()
            webDavClient.newCall(request).execute().use { checkResult(it) }
        }

    suspend fun delete(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(httpUrl)
                .method("DELETE", null)
                .build()
            webDavClient.newCall(request).execute().use { checkResult(it) }
            true
        }.onFailure {
            currentCoroutineContext().ensureActive()
        }.getOrDefault(false)
    }

    private fun propFindResponse(depth: Int): String? {
        val request = Request.Builder()
            .url(httpUrl)
            .header("Depth", depth.toString())
            .method("PROPFIND", PROP_FIND.toRequestBody("text/plain".toMediaType()))
            .build()
        return webDavClient.newCall(request).execute().use { response ->
            checkResult(response)
            response.body?.string()
        }
    }

    private fun parseBody(xml: String): List<WebDavFile> {
        val list = ArrayList<WebDavFile>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(StringReader(xml))

        var event = parser.eventType
        var href = ""
        var displayName = ""
        var contentType = ""
        var resourceType = ""
        var size = 0L
        var lastModify = 0L
        var inResponse = false
        var inResourceType = false

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val local = parser.name
                    when {
                        local.equals("response", ignoreCase = true) -> {
                            inResponse = true
                            href = ""
                            displayName = ""
                            contentType = ""
                            resourceType = ""
                            size = 0L
                            lastModify = 0L
                        }
                        !inResponse -> Unit
                        local.equals("href", ignoreCase = true) -> href = parser.nextText().orEmpty()
                        local.equals("displayname", ignoreCase = true) ->
                            displayName = parser.nextText().orEmpty()
                        local.equals("getcontenttype", ignoreCase = true) ->
                            contentType = parser.nextText().orEmpty()
                        local.equals("getcontentlength", ignoreCase = true) ->
                            size = parser.nextText()?.toLongOrNull() ?: 0L
                        local.equals("getlastmodified", ignoreCase = true) -> {
                            val text = parser.nextText().orEmpty()
                            lastModify = runCatching {
                                ZonedDateTime.parse(text, dateTimeFormatter)
                                    .toInstant().toEpochMilli()
                            }.getOrDefault(0L)
                        }
                        local.equals("resourcetype", ignoreCase = true) -> {
                            inResourceType = true
                            resourceType = ""
                        }
                        inResourceType -> resourceType += "<${parser.name}/>"
                    }
                }
                XmlPullParser.END_TAG -> {
                    val local = parser.name
                    when {
                        local.equals("resourcetype", ignoreCase = true) -> inResourceType = false
                        local.equals("response", ignoreCase = true) && inResponse -> {
                            inResponse = false
                            val hrefDecode = decodePath(href)
                            if (hrefDecode.isNotBlank()) {
                                val fileName = hrefDecode.removeSuffix("/").substringAfterLast("/")
                                val name = displayName.takeIf { it.isNotBlank() }
                                    ?.let { decodePath(it) }
                                    ?: fileName
                                var fullUrl = resolveUrl(httpUrl, hrefDecode)
                                if (WebDavFile.isDir(contentType, resourceType) &&
                                    !fullUrl.endsWith("/")
                                ) {
                                    fullUrl += "/"
                                }
                                list.add(
                                    WebDavFile(
                                        path = fullUrl,
                                        displayName = name,
                                        size = size,
                                        contentType = contentType,
                                        resourceType = resourceType,
                                        lastModify = lastModify,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
            event = parser.next()
        }
        return list
    }

    private fun checkResult(response: Response) {
        if (response.isSuccessful) return
        val body = response.body?.string().orEmpty()
        if (response.code == 401) {
            throw WebDavException("${httpUrl}\n401: Unauthorized")
        }
        if (body.contains("ObjectNotFound", ignoreCase = true)) {
            throw ObjectNotFoundException("$path doesn't exist. code:${response.code}")
        }
        val message = response.message.takeIf { it.isNotBlank() } ?: "未知错误"
        throw WebDavException("${httpUrl}\n${response.code}:$message")
    }

    private fun decodePath(raw: String): String =
        runCatching { URLDecoder.decode(raw, Charsets.UTF_8.name()) }.getOrDefault(raw)

    private fun resolveUrl(base: String, href: String): String {
        if (href.startsWith("http://") || href.startsWith("https://")) return href
        val baseUrl = base.toHttpUrl()
        return baseUrl.resolve(href)?.toString()
            ?: (baseUrl.toString().trimEnd('/') + "/" + href.trimStart('/'))
    }

    private fun pathsEqual(a: String, b: String): Boolean =
        a.trimEnd('/').equals(b.trimEnd('/'), ignoreCase = true)
}
