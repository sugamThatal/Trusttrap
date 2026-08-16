package com.trusttap.app.network

import com.trusttap.app.model.AnalysisResponse
import com.trusttap.app.model.CapabilitiesResponse
import com.trusttap.app.model.HealthResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface TrustTapApi {

    @GET("/")
    suspend fun healthCheck(): HealthResponse

    @GET("capabilities")
    suspend fun capabilities(): CapabilitiesResponse

    /**
     * claimedCaption is nullable because the backend treats it as optional
     * (Form(None) in FastAPI) - omit it entirely when there's no surrounding
     * text claim to check the image against.
     */
    @Multipart
    @POST("analyze-image")
    suspend fun analyzeImage(
        @Part image: MultipartBody.Part,
        @Part("claimed_caption") claimedCaption: RequestBody?
    ): AnalysisResponse

    /**
     * Same contract as analyzeImage, but for video: the backend samples
     * several frames from the clip and runs the same checks on them.
     */
    @Multipart
    @POST("analyze-video")
    suspend fun analyzeVideo(
        @Part video: MultipartBody.Part,
        @Part("claimed_caption") claimedCaption: RequestBody?
    ): AnalysisResponse

    @POST("analyze-text")
    suspend fun analyzeText(@Body request: TextAnalysisRequest): AnalysisResponse
}
