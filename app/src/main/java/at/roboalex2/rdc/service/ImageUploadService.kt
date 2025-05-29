package at.roboalex2.rdc.service

import android.content.Context
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File

object ImageUploadService {
    private val client = OkHttpClient()

    /**
     * Uploads an image file to Imgur anonymously and returns the public URL.
     * @throws Exception if upload fails
     */
    @Throws(Exception::class)
    fun uploadToImgur(ctx: Context, file: File): String {
        val mediaType = "image/jpeg".toMediaType()
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "image", file.name, file.asRequestBody(mediaType)
            )
            .build()

        val clientId = "9de367d66de86bc"
        val request = Request.Builder()
            .url("https://api.imgur.com/3/image")
            .addHeader("Authorization", "Client-ID $clientId")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val err = response.body?.string()
                Log.e("ImageUploadService", "Imgur error: $err")
                throw Exception("Imgur upload failed with code ${response.code}")
            }

            val bodyStr = response.body?.string() ?: throw Exception("Empty response")
            val json = JSONObject(bodyStr)
            return json.getJSONObject("data").getString("link")
        }
    }
}