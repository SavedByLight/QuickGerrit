package com.quickgerrit.app.data.api

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.quickgerrit.app.data.model.GerritAccount
import kotlinx.serialization.json.Json
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object GerritClientFactory {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    fun create(account: GerritAccount): GerritApi {
        val baseUrl = account.baseUrl.trimEnd('/') + "/"

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

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(xssiInterceptor)
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        return retrofit.create(GerritApi::class.java)
    }
}
