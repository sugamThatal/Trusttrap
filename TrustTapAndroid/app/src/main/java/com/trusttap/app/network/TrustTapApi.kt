package com.trusttap.app.network

import com.trusttap.app.model.AnalysisResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface TrustTapApi {

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
}
