package com.example.videoeditpoc

import android.app.Activity
import android.app.ProgressDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.SpannableString
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
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
import com.example.videoeditpoc.databinding.ActivityInsertEmojiBinding
import com.google.common.collect.ImmutableList
import com.vanniktech.emoji.EmojiPopup
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

class InsertEmojiActivity : AppCompatActivity() {
    private lateinit var binding: ActivityInsertEmojiBinding
    private var input_video_uri_media: Uri? = null
    private var outputFilePath: String? = null
    var emojiFilePath: String? = null

    @RequiresApi(Build.VERSION_CODES.M)
    @OptIn(androidx.media3.common.util.UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInsertEmojiBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.insertEmojiBtn.setOnClickListener {
            if (binding.etEmoji.text!!.isEmpty()) {
                Toast.makeText(this, "Please insert emojis", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            insertEmoji()
        }

        binding.insertTextActBtn.setOnClickListener {
            startActivity(Intent(this, InsertTextActivity::class.java))
        }
        val emojiPopup =
            EmojiPopup.Builder.fromRootView(binding.insertGraphicRl).build(binding.etEmoji)

        binding.btnEmojis.setOnClickListener {
            emojiPopup.toggle()
        }
    }

    @OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun insertEmoji() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "video/*"
        emojiLauncher.launch(intent)
    }

    @OptIn(UnstableApi::class)
    val emojiLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK && result.data == null) {
                return@registerForActivityResult
            }
            input_video_uri_media = result.data!!.data
            Log.d("gamedia", "input_video_uri_media: $input_video_uri_media")
            Toast.makeText(this, "mediaItemLoadSuccess:$input_video_uri_media", Toast.LENGTH_SHORT)
                .show()
            if (input_video_uri_media != null && "content" == input_video_uri_media!!.scheme) {
                val cursor = this.contentResolver.query(
                    input_video_uri_media!!,
                    arrayOf(MediaStore.Images.ImageColumns.DATA),
                    null,
                    null,
                    null
                )
                cursor!!.moveToFirst()
                emojiFilePath = cursor.getString(0)
                Log.d("filePathScheme", "filePath: $emojiFilePath")
                cursor.close()
            } else {
                emojiFilePath = input_video_uri_media!!.path
                Log.d("filePathwoScheme", "filePathwosc: $emojiFilePath ")
            }
            val mediaItem = MediaItem.Builder()
                .setUri(input_video_uri_media)
                .build()

            Log.d("gamedia", "mediaItem: $mediaItem")
            createTransformation(mediaItem)


        }


    private fun createVideoEffects(): ImmutableList<Effect> {
        val effects = ImmutableList.Builder<Effect>()
        val overlayEffect: OverlayEffect = createOverlayEffect()!!
        effects.add(overlayEffect)
        return effects.build()
    }

    @OptIn(UnstableApi::class)
    private fun createOverlayEffect(): OverlayEffect? {
        val overLaysBuilder: ImmutableList.Builder<TextureOverlay> = ImmutableList.builder()
        val getEmoji = binding.etEmoji.text.toString()
        val overlayEmoji = SpannableString(getEmoji)
        val emojiTextureOverlay: TextureOverlay =
            TextOverlay.createStaticTextOverlay(overlayEmoji)
        overLaysBuilder.add(emojiTextureOverlay)

        val overlays: ImmutableList<TextureOverlay> = overLaysBuilder.build()
        return if (overlays.isEmpty()) null else OverlayEffect(overlays)
    }

    @OptIn(UnstableApi::class)
    private fun createTransformation(mediaItem: MediaItem) {

        val progressDialog = ProgressDialog(this@InsertEmojiActivity)
        progressDialog.setCancelable(false)
        progressDialog.setMessage("Applying Filter..")
        progressDialog.setCanceledOnTouchOutside(false)
        progressDialog.show()
        val request = TransformationRequest.Builder().setVideoMimeType(MimeTypes.VIDEO_H264).build()
        val transformerListener: Transformer.Listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, result: ExportResult) {
                Log.d("vcas", "success")
                progressDialog.dismiss()
                Toast.makeText(this@InsertEmojiActivity, "Filter Applied", Toast.LENGTH_SHORT)
                    .show()

            }

            override fun onError(
                composition: Composition, result: ExportResult, exception: ExportException
            ) {
                Log.d("vcae", "fail")
                progressDialog.dismiss()
                Toast.makeText(
                    this@InsertEmojiActivity, "Something Went Wrong!", Toast.LENGTH_SHORT
                ).show()
            }
        }

        val inputEditedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setEffects(
                Effects(listOf(), createVideoEffects())
            ).build()
        val transformer = Transformer.Builder(this).setTransformationRequest(request)
            .addListener(transformerListener).build()
        outputFilePath = getOutputFilePath()
        transformer.start(inputEditedMediaItem, outputFilePath!!)
    }

    private fun getOutputFilePath(): String? {

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