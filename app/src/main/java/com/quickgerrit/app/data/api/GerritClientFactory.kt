package com.quickgerrit.app.data.api

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.quickgerrit.app.BuildConfig
import com.quickgerrit.app.data.model.GerritAccount
import com.quickgerrit.app.util.AppLog
import kotlinx.serialization.json.Json
import okhttp3.ConnectionPool
import okhttp3.ConnectionSpec
import okhttp3.Credentials
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.TlsVersion
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Builds authenticated Retrofit clients for Gerrit.
 *
 * Compatibility notes for older Gerrit / nginx stacks (e.g. nginx 1.10 + old Gerrit):
 * - Force HTTP/1.1 (HTTP/2 + Basic auth is flaky on ancient nginx)
 * - Always allow cleartext-style TLS trust-all (self-signed / expired lab certs)
 * - Prefer TLS 1.2 but keep broader ConnectionSpec for legacy endpoints
 * - Trim credentials and encode Basic auth as UTF-8
 */
object GerritClientFactory {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = false
        explicitNulls = false
    }

    private val clientCache = ConcurrentHashMap<String, GerritApi>()

    private val sharedConnectionPool = ConnectionPool(
        maxIdleConnections = 8,
        keepAliveDuration = 5,
        timeUnit = TimeUnit.MINUTES
    )

    private val sharedDispatcher = Dispatcher().apply {
        maxRequests = 32
        maxRequestsPerHost = 8
    }

    private val trustAllManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    private val trustAllSslSocketFactory by lazy {
        SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(trustAllManager), SecureRandom())
        }.socketFactory
    }

    private val trustAllHostnameVerifier = HostnameVerifier { _, _ -> true }

    private fun cacheKey(account: GerritAccount): String {
        val user = account.username.trim()
        val pass = account.httpPassword.trim()
        return "${account.id}|${normalizeBaseUrl(account.baseUrl)}|$user|${pass.hashCode()}"
    }

    /** Canonical base URL with trailing slash for Retrofit. */
    fun normalizeBaseUrl(raw: String): String {
        var u = raw.trim()
        if (u.isEmpty()) return "https://localhost/"
        // Strip trailing /a or /a/ that users sometimes paste
        u = u.removeSuffix("/").removeSuffix("/a").removeSuffix("/")
        return u.trimEnd('/') + "/"
    }

    fun invalidate(accountId: String? = null) {
        if (accountId == null) {
            clientCache.clear()
        } else {
            clientCache.keys.filter { it.startsWith("$accountId|") }.forEach { clientCache.remove(it) }
        }
    }

    fun create(account: GerritAccount): GerritApi {
        val key = cacheKey(account)
        clientCache[key]?.let { return it }
        return clientCache.computeIfAbsent(key) { buildClient(account) }
    }

    private fun buildClient(account: GerritAccount): GerritApi {
        val baseUrl = normalizeBaseUrl(account.baseUrl)
        val user = account.username.trim()
        val pass = account.httpPassword.trim()

        AppLog.d(
            "Creating Gerrit client for ${account.name.ifBlank { user }} @ $baseUrl " +
                "(userLen=${user.length} passLen=${pass.length} http1.1)"
        )
        if (user.isEmpty() || pass.isEmpty()) {
            AppLog.e("Refusing client with blank username or HTTP password")
        }

        // UTF-8 Basic — matches modern clients; pure ASCII passwords are identical to ISO-8859-1
        val credential = Credentials.basic(user, pass, StandardCharsets.UTF_8)

        val authInterceptor = Interceptor { chain ->
            val original = chain.request()
            // Always send Basic for /a/ routes; Gerrit expects this and does not use Digest by default
            val request = original.newBuilder()
                .header("Authorization", credential)
                .header("Accept", "application/json")
                // Some old proxies treat missing UA poorly
                .header("User-Agent", "QuickGerrit/${BuildConfig.VERSION_NAME}")
                .build()
            val response = chain.proceed(request)
            if (response.code == 401) {
                AppLog.w(
                    "Gerrit 401 for ${original.method} ${original.url} " +
                        "(userLen=${user.length} passLen=${pass.length}). " +
                        "Check HTTP password + exact username. WWW-Authenticate=" +
                        response.header("WWW-Authenticate")
                )
            }
            response
        }

        // Strip Gerrit XSSI prefix )]}'  (and variants without newline)
        val xssiInterceptor = Interceptor { chain ->
            val response = chain.proceed(chain.request())
            val body = response.body ?: return@Interceptor response
            val contentType = body.contentType()
            val content = body.string()
            val cleaned = when {
                content.startsWith(")]}'\n") -> content.substring(5)
                content.startsWith(")]}'\r\n") -> content.substring(6)
                content.startsWith(")]}'") -> content.substring(4).trimStart('\n', '\r', ' ')
                else -> content
            }
            response.newBuilder()
                .body(cleaned.toResponseBody(contentType))
                .build()
        }

        val logging = HttpLoggingInterceptor { message ->
            if (message.length > 500) {
                AppLog.d(message.take(200) + "… (${message.length} chars)", tag = "QuickGerrit.Http")
            } else {
                AppLog.d(message, tag = "QuickGerrit.Http")
            }
        }.apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.HEADERS
            } else {
                HttpLoggingInterceptor.Level.BASIC
            }
            redactHeader("Authorization")
        }

        // Modern + compatible TLS; COMPATIBLE_TLS helps older endpoints
        val modernTls = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
            .tlsVersions(TlsVersion.TLS_1_2, TlsVersion.TLS_1_3)
            .build()
        val compatibleTls = ConnectionSpec.COMPATIBLE_TLS
        val cleartext = ConnectionSpec.CLEARTEXT

        val builder = OkHttpClient.Builder()
            .dispatcher(sharedDispatcher)
            .connectionPool(sharedConnectionPool)
            // Critical for old nginx (e.g. 1.10.x on some Gerrit hosts): HTTP/2 breaks Basic auth
            .protocols(listOf(Protocol.HTTP_1_1))
            .connectionSpecs(listOf(modernTls, compatibleTls, cleartext))
            .addInterceptor(authInterceptor)
            .addInterceptor(xssiInterceptor)
            .addInterceptor(logging)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            // Always trust for lab / outdated Gerrit TLS (matches Android debug behavior)
            .sslSocketFactory(trustAllSslSocketFactory, trustAllManager)
            .hostnameVerifier(trustAllHostnameVerifier)

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(builder.build())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        return retrofit.create(GerritApi::class.java)
    }
}
