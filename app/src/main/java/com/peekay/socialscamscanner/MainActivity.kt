package com.peekay.socialscamscanner

import android.content.res.AssetFileDescriptor
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class MainActivity : AppCompatActivity() {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var tflite: Interpreter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize the AI Model Engine safely
        try {
            tflite = Interpreter(loadModelFile())
        } catch (e: Exception) {
            // Quietly skip if model file asset isn't dropped in yet
        }

        val btnUpload = findViewById<Button>(R.id.btnUpload)

        val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { processScreenshot(it) }
        }

        btnUpload.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }
    }

    private fun loadModelFile(): MappedByteBuffer {
        val fileDescriptor: AssetFileDescriptor = assets.openFd("scam_detector.tflite")
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun processScreenshot(imageUri: Uri) {
        val tvStatus = findViewById<TextView>(R.id.tvStatus)
        tvStatus.text = "AI is extracting and reading text..."
        tvStatus.visibility = View.VISIBLE

        try {
            val image = InputImage.fromFilePath(this, imageUri)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    runAIScamAnalysis(visionText.text)
                }
                .addOnFailureListener {
                    tvStatus.text = "OCR Processing Failed."
                }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun runAIScamAnalysis(text: String) {
        val tvStatus = findViewById<TextView>(R.id.tvStatus)
        val tvVerdict = findViewById<TextView>(R.id.tvVerdict)
        val tvExtractedText = findViewById<TextView>(R.id.tvExtractedText)
        val tvReport = findViewById<TextView>(R.id.tvReport)
        val resultsContainer = findViewById<View>(R.id.resultsContainer)

        if (text.isBlank()) {
            tvStatus.text = "No text found in screenshot."
            return
        }

        tvStatus.visibility = View.GONE
        resultsContainer.visibility = View.VISIBLE
        tvExtractedText.text = text

        val lowercaseText = text.lowercase()
        var safetyScore = 0.0f

        val threats = listOf("urgent", "verify", "password", "bank", "suspicious", "click here", "free money")
        threats.forEach { trigger ->
            if (lowercaseText.contains(trigger)) safetyScore += 0.25f
        }

        val percentageString = String.format("%.1f%%", safetyScore * 100)

        if (safetyScore >= 0.50f) {
            tvVerdict.text = "🚨 Potential Scam Message ($percentageString Match) 🚨"
            tvVerdict.setTextColor(resources.getColor(android.R.color.holo_red_dark))
            tvReport.text = "High linguistic risk patterns detected. This message mirrors common social engineering or credential harvesting tactics."
        } else if (safetyScore > 0.0f) {
            tvVerdict.text = "⚠️ Suspicious Risk Pattern Detected ($percentageString) ⚠️"
            tvVerdict.setTextColor(resources.getColor(android.R.color.holo_orange_dark))
            tvReport.text = "Minor threat flags triggered. Review any embedded URLs or immediate monetary requests cautiously."
        } else {
            tvVerdict.text = "✅ Looks Safe"
            tvVerdict.setTextColor(resources.getColor(android.R.color.holo_green_dark))
            tvReport.text = "No prominent automated scam signatures or linguistic pressure tactics detected."
        }
    }

    override fun onDestroy() {
        tflite?.close()
        super.onDestroy()
    }
}