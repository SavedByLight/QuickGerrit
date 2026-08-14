package com.quickgerrit.app.data.api

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.quickgerrit.app.BuildConfig
import com.quickgerrit.app.data.model.GerritAccount
import com.quickgerrit.app.util.AppLog
import kotlinx.serialization.json.Json
import okhttp3.ConnectionPool
import okhttp3.Credentials
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object GerritClientFactory {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = false
        explicitNulls = false
    }

    /** Reuse OkHttp + Retrofit per account — creating them per request was a major cost. */
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

    private fun cacheKey(account: GerritAccount): String =
        "${account.id}|${account.baseUrl.trimEnd('/')}|${account.username}|${account.httpPassword.hashCode()}"

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
        val baseUrl = account.baseUrl.trimEnd('/') + "/"
        AppLog.d("Creating Gerrit client for ${account.name.ifBlank { account.username }} @ $baseUrl")

        val credential = Credentials.basic(account.username, account.httpPassword)

        val authInterceptor = Interceptor { chain ->
            val request = chain.request().newBuilder()
                .header("Authorization", credential)
                .header("Accept", "application/json")
                .build()
            chain.proceed(request)
        }

        // Strip Gerrit XSSI prefix )]}'
        val xssiInterceptor = Interceptor { chain ->
            val response = chain.proceed(chain.request())
            val body = response.body ?: return@Interceptor response
            val contentType = body.contentType()
            val content = body.string()
            val cleaned = when {
                content.startsWith(")]}'\n") -> content.substring(5)
                content.startsWith(")]}'") -> content.substring(4).trimStart('\n', '\r')
                else -> content
            }
            response.newBuilder()
                .body(cleaned.toResponseBody(contentType))
                .build()
        }

        // BODY logging of multi‑100KB change lists is extremely slow. Use HEADERS in
        // debug (still useful) and BASIC in release. Errors still surface via AppLog.
        val logging = HttpLoggingInterceptor { message ->
            // Avoid stuffing huge lines into the in-memory log buffer
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

        val builder = OkHttpClient.Builder()
            .dispatcher(sharedDispatcher)
            .connectionPool(sharedConnectionPool)
            .addInterceptor(authInterceptor)
            .addInterceptor(xssiInterceptor)
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)

        if (BuildConfig.DEBUG) {
            builder
                .sslSocketFactory(trustAllSslSocketFactory, trustAllManager)
                .hostnameVerifier(trustAllHostnameVerifier)
        }

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(builder.build())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        return retrofit.create(GerritApi::class.java)
    }
}
