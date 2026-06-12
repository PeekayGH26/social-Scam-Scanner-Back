package com.peekay.socialscamscanner

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val httpClient = OkHttpClient()

    // 🔗 YOUR LIVE RENDER ENDPOINT (UPDATED)
    private val targetUrl = "https://social-scam-scanner-backend-2.onrender.com/analyze"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnUpload = findViewById<Button>(R.id.btnUpload)
        val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { processScreenshot(it) }
        }

        btnUpload.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }
    }

    private fun processScreenshot(imageUri: Uri) {
        val tvStatus = findViewById<TextView>(R.id.tvStatus)
        tvStatus.text = "AI is extracting and reading text..."
        tvStatus.visibility = View.VISIBLE

        try {
            val image = InputImage.fromFilePath(this, imageUri)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    // Send the extracted text to your hosted cloud backend
                    sendTextToBackend(visionText.text)
                }
                .addOnFailureListener {
                    tvStatus.text = "OCR Processing Failed."
                }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun sendTextToBackend(extractedText: String) {
        val tvStatus = findViewById<TextView>(R.id.tvStatus)

        // Build the JSON payload to match your FastAPI ScamRequest model
        val json = JSONObject().put("text", extractedText)
        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val request = Request.Builder().url(targetUrl).post(body).build()

        // Execute asynchronous network call
        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    tvStatus.text = "Network connection to AI server failed."
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let { responseData ->
                    runOnUiThread {
                        updateUIWithResults(extractedText, responseData)
                    }
                }
            }
        })
    }

    private fun updateUIWithResults(rawText: String, jsonResponse: String) {
        val tvStatus = findViewById<TextView>(R.id.tvStatus)
        val tvVerdict = findViewById<TextView>(R.id.tvVerdict)
        val tvExtractedText = findViewById<TextView>(R.id.tvExtractedText)
        val tvReport = findViewById<TextView>(R.id.tvReport)
        val resultsContainer = findViewById<View>(R.id.resultsContainer)

        try {
            val resObj = JSONObject(jsonResponse)
            val serverVerdict = resObj.optString("verdict", "")
            val safetyScore = resObj.optDouble("safety_score", 0.0)
            val threatTags = resObj.optJSONArray("threat_tags")

            tvStatus.visibility = View.GONE
            resultsContainer.visibility = View.VISIBLE
            tvExtractedText.text = rawText

            // Map standard threat tags array layout
            val tagList = mutableListOf<String>()
            if (threatTags != null) {
                for (i in 0 until threatTags.length()) {
                    tagList.add(threatTags.getString(i))
                }
            }
            val tagsString = if (tagList.isNotEmpty()) "\nTriggered flags: ${tagList.joinToString(", ")}" else ""

            // Comprehensive mapping logic: if the server flags a scam OR our typo fallback rules find multiple hits
            if (serverVerdict.contains("🚨") || tagList.size >= 3 || safetyScore >= 0.60) {
                tvVerdict.text = "🚨 Potential Scam Message 🚨"
                tvVerdict.setTextColor(resources.getColor(android.R.color.holo_red_dark))
                tvReport.text = "High risk metrics detected! This text matches verified social engineering patterns and lottery spam vectors.$tagsString"
            } else if (serverVerdict.contains("⚠️") || tagList.isNotEmpty() || safetyScore > 0.15) {
                tvVerdict.text = "⚠️ Suspicious Risk Pattern Detected ⚠️"
                tvVerdict.setTextColor(resources.getColor(android.R.color.holo_orange_dark))
                tvReport.text = "Suspicious markers or urgency indicators found. Treat any embedded links or phone instructions carefully.$tagsString"
            } else {
                tvVerdict.text = "✅ Looks Safe"
                tvVerdict.setTextColor(resources.getColor(android.R.color.holo_green_dark))
                tvReport.text = "No prominent scam signatures detected by the cloud model verification pass."
            }

        } catch (e: Exception) {
            tvStatus.text = "Failed parsing metrics from AI backend."
            e.printStackTrace()
        }
    }
}
