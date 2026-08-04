package com.trusttap.app.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    /**
     * 10.0.2.2 is the Android EMULATOR's special alias for "the host
     * machine's localhost" - use this while the FastAPI server runs on
     * the same laptop as Android Studio.
     *
     * Testing on a REAL phone instead? Both devices need to be on the same
     * Wi-Fi, and this must be your laptop's actual LAN IP, e.g.:
     *   "http://192.168.1.42:8000/"
     * Find it with `ipconfig getifaddr en0` (Mac) or `ipconfig` (Windows).
     */
    private const val BASE_URL = "http://10.0.2.2:8000/"

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(logging)
        .connectTimeout(30, TimeUnit.SECONDS)
        // BLIP + CLIP inference on a CPU can take a while - don't time out early
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    val api: TrustTapApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TrustTapApi::class.java)
    }
}
