package com.trusttap.app.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private val logging = HttpLoggingInterceptor().apply {
        // Useful during development, but avoid logging request bodies in a
        // production build because shared photos may be sensitive.
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(logging)
        .connectTimeout(30, TimeUnit.SECONDS)
        // Uploading a video file takes longer than a photo - OkHttp's
        // default writeTimeout is only 10s, too short for that.
        .writeTimeout(60, TimeUnit.SECONDS)
        // Video analysis runs the AI-detector across several sampled
        // frames sequentially on CPU, on top of BLIP+CLIP - meaningfully
        // slower than a single image, so this needs real headroom.
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    private val apiCache = mutableMapOf<String, TrustTapApi>()

    @Synchronized
    fun api(baseUrl: String): TrustTapApi {
        val normalizedUrl = normalizeBaseUrl(baseUrl)
        return apiCache.getOrPut(normalizedUrl) {
            Retrofit.Builder()
                .baseUrl(normalizedUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(TrustTapApi::class.java)
        }
    }

    fun normalizeBaseUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim()
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }
}
