package com.example.videoeditpoc

import android.app.Activity
import android.app.ProgressDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.TextOverlay
import androidx.media3.effect.TextureOverlay
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.TransformationRequest
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.example.videoeditpoc.databinding.ActivityInsertTextBinding
import com.google.common.collect.ImmutableList
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream


@UnstableApi
class InsertTextActivity : AppCompatActivity() {
    lateinit var binding: ActivityInsertTextBinding
    private var input_video_uri_ffmpeg: Uri? = null
    private var outputFilePath: String? = null
    var videoFilePath: String? = null
    private var player: ExoPlayer? = null
    private var playWhenReady = true
    private var mediaItemIndex = 0
    private var playbackPosition = 0L
    private var currentItem = 0
    val handler = Handler(Looper.getMainLooper())
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInsertTextBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.insertTextBtn.setOnClickListener {
            if (binding.ediText.text.toString().isEmpty()) {
                Toast.makeText(this, "enter text", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "video/*"
            selectVideoLauncherUsingFfmpeg.launch(intent)
        }

        binding.insertGraphicActivityBtn.setOnClickListener {
            startActivity(Intent(this, InsertGraphicActivity::class.java))
        }

        initExoPLayer()
    }

    private fun initExoPLayer() {
//        val renderFactory = DefaultRenderersFactory(this)
//        renderFactory.setEnableDecoderFallback(true)
        player = ExoPlayer.Builder(this).build()
//        binding.exoPlayer.player = player


    }

    private val selectVideoLauncherUsingFfmpeg =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->

            if (result.resultCode != Activity.RESULT_OK && result.data == null) {
                return@registerForActivityResult
            }
            input_video_uri_ffmpeg = result.data!!.data

            Log.d("ita", "input_video_uri_ffmpeg: $input_video_uri_ffmpeg")
            Toast.makeText(this, "mediaItemLoadSuccess:$input_video_uri_ffmpeg", Toast.LENGTH_SHORT)
                .show()

            if (input_video_uri_ffmpeg != null && "content" == input_video_uri_ffmpeg!!.scheme) {
                val cursor = this.contentResolver.query(
                    input_video_uri_ffmpeg!!,
                    arrayOf(MediaStore.Images.ImageColumns.DATA),
                    null,
                    null,
                    null
                )
                cursor!!.moveToFirst()
                videoFilePath = cursor.getString(0)
                Log.d("filePathScheme", "filePath: $videoFilePath")
                cursor.close()
            } else {
                videoFilePath = input_video_uri_ffmpeg!!.path
                Log.d("filePathwoScheme", "filePathwosc: $videoFilePath ")
            }
            val mediaItem = MediaItem.Builder().setUri(input_video_uri_ffmpeg).build()
            createTransformation(mediaItem)

        }

    private fun playbackStateListener() = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            // super.onPlaybackStateChanged(playbackState)

            val stateString: String = when (playbackState) {
                ExoPlayer.STATE_IDLE -> "ExoPlayer.STATE_IDLE"
                ExoPlayer.STATE_BUFFERING -> "ExoPlayer.STATE_BUFFERING"
                ExoPlayer.STATE_READY -> "ExoPlayer.STATE_READY"
                ExoPlayer.STATE_ENDED -> "ExoPlayer.STATE_ENDED"
                else -> "Unknown state"
            }

            Log.d("ita", "onPlaybackStateChanged: changed state to $stateString ")
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            // super.onIsPlayingChanged(isPlaying)
            val playingString = if (isPlaying) "PLAYING" else "NOT PLAYING"
            Log.d("ita", "onIsPlayingChanged: Player is currently $playingString ")
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
        outputFilePath =
            createExternalCacheFile(System.currentTimeMillis().toString() + ".mp4").absolutePath
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


    @Throws(IOException::class)
    private fun createExternalCacheFile(fileName: String): File {
        val file = File(externalCacheDir, fileName)
        check(!(file.exists() && !file.delete())) { "Could not delete the previous export output file" }
        check(file.createNewFile()) { "Could not create the export output file" }
        return file
    }

    override fun onStop() {
        super.onStop()
        player?.let { exoPlayer ->
            playbackPosition = exoPlayer.currentPosition
            mediaItemIndex = exoPlayer.currentMediaItemIndex
            playWhenReady = exoPlayer.playWhenReady
            exoPlayer.removeListener(playbackStateListener())
            exoPlayer.release()
        }
        player = null
    }

    override fun onStart() {
        super.onStart()
        initExoPLayer()
    }

    override fun onResume() {
        super.onResume()
        if (player == null) initExoPLayer()
    }

    override fun onPause() {
        super.onPause()
        player?.let { exoPlayer ->
            playbackPosition = exoPlayer.currentPosition
            mediaItemIndex = exoPlayer.currentMediaItemIndex
            playWhenReady = exoPlayer.playWhenReady
            exoPlayer.removeListener(playbackStateListener())
            exoPlayer.release()
        }
        player = null
    }
}