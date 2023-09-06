package com.example.videoeditpoc

import android.app.ProgressDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Environment
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.TextOverlay
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.TransformationRequest
import androidx.media3.transformer.Transformer
import com.example.videoeditpoc.databinding.ActivityInsertTextBinding
import com.google.common.collect.ImmutableList
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date


@UnstableApi
class InsertTextActivity : AppCompatActivity() {
    lateinit var binding: ActivityInsertTextBinding
    private var input_video_uri_ffmpeg: String? = null
    private var outputFilePath: String? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInsertTextBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.selectVideoBtn.setOnClickListener {
            selectVideoLauncherUsingFfmpeg.launch("video/*")
        }

        binding.insertGraphicActivityBtn.setOnClickListener {
            startActivity(Intent(this, InsertGraphicActivity::class.java))
        }

        binding.insertTextBtn.setOnClickListener {

            if (input_video_uri_ffmpeg == null) {
                Toast.makeText(this, "upload video", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (binding.ediText.text.toString().isEmpty()) {
                Toast.makeText(this, "enter text", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            insertText()
        }
    }

    private fun insertText() {
        val mediaItem = MediaItem.Builder().setUri(input_video_uri_ffmpeg).build()
        createTransformation(mediaItem)
    }

    private val selectVideoLauncherUsingFfmpeg =
        registerForActivityResult(ActivityResultContracts.GetContent()) {
            it?.let {
                input_video_uri_ffmpeg = it.toString()
                Toast.makeText(
                    this,
                    "video loaded successfully: $input_video_uri_ffmpeg",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }


    private fun createVideoEffects(): ImmutableList<Effect> {
        val effects = ImmutableList.Builder<Effect>()
        val overlayEffect: OverlayEffect = createOverlayEffect()!!
        effects.add(overlayEffect)
        return effects.build()
    }

    private fun createOverlayEffect(): OverlayEffect? {
        val overLaysBuilder: ImmutableList.Builder<TextureOverlay> = ImmutableList.builder()

        val getUserInput = binding.ediText.text.toString()
        val spannableText = SpannableString(getUserInput)
        spannableText.setSpan(
            ForegroundColorSpan(Color.GREEN),
            0,
            spannableText.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        val overlayText: TextureOverlay = TextOverlay.createStaticTextOverlay(spannableText)
        overLaysBuilder.add(overlayText)


        val overlays: ImmutableList<TextureOverlay> = overLaysBuilder.build()
        return if (overlays.isEmpty()) null else OverlayEffect(overlays)
    }

    private fun createTransformation(mediaItem: MediaItem) {

        val inputEditedMediaItem = EditedMediaItem.Builder(mediaItem).setEffects(
            Effects(listOf(), createVideoEffects())
        ).build()
        val transformer = transformerBuilder()
        outputFilePath = getFilePath()
        Log.d("ita", "createTransformation:$outputFilePath")
        transformer.start(inputEditedMediaItem, outputFilePath!!)
    }

    private fun transformerBuilder(): Transformer {


        val progressDialog = ProgressDialog(this@InsertTextActivity)
        progressDialog.setCancelable(false)
        progressDialog.setMessage("Applying Filter..")
        progressDialog.setCanceledOnTouchOutside(false)
        progressDialog.show()
        val request = TransformationRequest.Builder().setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC).build()
        val transformerListener: Transformer.Listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, result: ExportResult) {
                Log.d("vcas", "success")

                progressDialog.dismiss()
                Toast.makeText(this@InsertTextActivity, "Filter Applied", Toast.LENGTH_SHORT).show()
            }

            override fun onError(
                composition: Composition, result: ExportResult, exception: ExportException
            ) {
                Log.d("vcae", "fail")
                progressDialog.dismiss()
                Toast.makeText(this@InsertTextActivity, "Something went wrong", Toast.LENGTH_SHORT)
                    .show()
            }
        }
        return Transformer.Builder(this).setTransformationRequest(request)
            .addListener(transformerListener).build()
    }

    private fun getFilePath(): String? {

        val currentTimeMillis = System.currentTimeMillis()
        val today = Date(currentTimeMillis)
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss")
        val fileName: String = "media3_" + dateFormat.format(today) + ".mp4"

        val documentsDirectory =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).absoluteFile
        Log.d("itadocdir", "getDocDirName:$documentsDirectory")
        val mediaThreeDirectory = File(documentsDirectory, "Media3")
        if (!mediaThreeDirectory.exists()) {
            mediaThreeDirectory.mkdir()
        }
        val file = File(mediaThreeDirectory, fileName)


        file.createNewFile()
        println("No file found file created ${file.absolutePath}")


        return file.absolutePath
    }

}