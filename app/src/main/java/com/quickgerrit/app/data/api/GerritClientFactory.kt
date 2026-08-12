package com.quickgerrit.app.data.api

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.quickgerrit.app.BuildConfig
import com.quickgerrit.app.data.model.GerritAccount
import com.quickgerrit.app.util.AppLog
import kotlinx.serialization.json.Json
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.security.SecureRandom
import java.security.cert.X509Certificate
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
        encodeDefaults = false   // omit nulls / defaults — Gerrit rejects unknown/null quirks
        explicitNulls = false
    }

    /**
     * Debug-only trust manager that accepts any certificate chain.
     * NEVER used in release builds.
     */
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

    fun create(account: GerritAccount): GerritApi {
        val baseUrl = account.baseUrl.trimEnd('/') + "/"
        AppLog.d("Creating Gerrit client for ${account.name.ifBlank { account.username }} @ $baseUrl")

        val authInterceptor = Interceptor { chain ->
            val request = chain.request().newBuilder()
                .header("Authorization", Credentials.basic(account.username, account.httpPassword))
                .header("Accept", "application/json")
                .build()
            chain.proceed(request)
        }

        // Gerrit prefixes JSON with )]}' to prevent XSSI
        val xssiInterceptor = Interceptor { chain ->
            val response = chain.proceed(chain.request())
            val body = response.body ?: return@Interceptor response
            val content = body.string()
            val cleaned = if (content.startsWith(")]}'")) {
                content.substringAfter("\n").ifEmpty { content.removePrefix(")]}'") }
            } else content
            response.newBuilder()
                .body(cleaned.toResponseBody(body.contentType()))
                .build()
        }

        val logging = HttpLoggingInterceptor { message ->
            AppLog.d(message, tag = "QuickGerrit.Http")
        }.apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.BASIC
            }
        }

        val builder = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(xssiInterceptor)
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)

        if (BuildConfig.DEBUG) {
            AppLog.w("DEBUG build: trusting all SSL certificates (insecure)")
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
