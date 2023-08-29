package com.example.videoeditpoc

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.TextOverlay
import androidx.media3.effect.TextureOverlay
import androidx.media3.exoplayer.ExoPlayer
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
import java.io.IOException

class InsertEmojiActivity : AppCompatActivity() {
    private lateinit var binding: ActivityInsertEmojiBinding
    private var input_video_uri_media: String? = null
    lateinit var exoPlayer: ExoPlayer
    var videoFilePath: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInsertEmojiBinding.inflate(layoutInflater)
        setContentView(binding.root)

        exoPlayer = ExoPlayer.Builder(this).build()
        binding.playerView.player = exoPlayer

        binding.insertEmojiBtn.setOnClickListener {
            if (binding.etEmoji.text!!.isEmpty()) {
                Toast.makeText(this, "Please insert emojis", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            insertEmoji()
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
            input_video_uri_media = result.data!!.data.toString()
            Log.d("gamedia", "input_video_uri_media: $input_video_uri_media")

//            val prefs = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
//            val editor = prefs.edit()
//            editor.putString("inputVideoUriMedia", input_video_uri_media!!.toString())
//            editor.apply()

            val mediaItem = MediaItem.Builder()
                .setUri(input_video_uri_media)
                .setMimeType(MimeTypes.VIDEO_H265)
                .build()

            Log.d("gamedia", "mediaItem: $mediaItem")
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.play()
            exoPlayer.setVideoEffects(createVideoEffects())
            createTransformation(mediaItem)

            // binding.etEmoji.text!!.clear()


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
        val overlaySettings = OverlaySettings.Builder().build()

        val getEmoji = binding.etEmoji.text.toString()
        val overlayEmoji = SpannableString(getEmoji)
//        overlayEmoji.setSpan(
//            ForegroundColorSpan(Color.BLUE),
//            0,
//            overlayEmoji.length,
//            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
//        )
        val emojiTextureOverlay: TextureOverlay =
            TextOverlay.createStaticTextOverlay(overlayEmoji)
        overLaysBuilder.add(emojiTextureOverlay)

//        binding.etEmoji.addTextChangedListener(object : TextWatcher{
//            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
//
//            }
//
//            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
//
//            }
//
//            override fun afterTextChanged(s: Editable?) {
//                Log.d("imta", "afterTextChanged: $s")
//                val updatedEmoji = SpannableString(s.toString())
//                val updatedEmojiTextureOverlay: TextureOverlay =
//                    TextOverlay.createStaticTextOverlay(updatedEmoji)
//                val updatedOverLaysBuilder: ImmutableList.Builder<TextureOverlay> = ImmutableList.builder()
//                updatedOverLaysBuilder.add(updatedEmojiTextureOverlay)
//
//            }
//
//        })

        val overlays: ImmutableList<TextureOverlay> = overLaysBuilder.build()
        return if (overlays.isEmpty()) null else OverlayEffect(overlays)
    }

    @OptIn(UnstableApi::class)
    private fun createTransformation(mediaItem: MediaItem) {
        val request = TransformationRequest.Builder().setVideoMimeType(MimeTypes.VIDEO_H265).build()
        val transformerListener: Transformer.Listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, result: ExportResult) {
                Log.d("vcas", "success")
                Toast.makeText(this@InsertEmojiActivity, "success: $result", Toast.LENGTH_SHORT)
                    .show()
            }

            override fun onError(
                composition: Composition, result: ExportResult, exception: ExportException
            ) {
                Log.d("vcae", "fail")
            }
        }

        val inputEditedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setEffects(
                Effects(listOf(), createVideoEffects())
            ).build()
        val transformer = Transformer.Builder(this).setTransformationRequest(request)
            .addListener(transformerListener).build()
        val filePath: String = createExternalCacheFile("transformer.mp4").absolutePath
        transformer.start(inputEditedMediaItem, filePath)
    }

    @Throws(IOException::class)
    private fun createExternalCacheFile(fileName: String): File {
        val file = File(externalCacheDir, fileName)
        check(!(file.exists() && !file.delete())) { "Could not delete the previous export output file" }
        check(file.createNewFile()) { "Could not create the export output file" }
        return file
    }

}