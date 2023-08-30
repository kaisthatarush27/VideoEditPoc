package com.example.videoeditpoc

import android.Manifest
import android.app.Activity
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
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
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.TextOverlay
import androidx.media3.effect.TextureOverlay
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.TransformationRequest
import androidx.media3.transformer.Transformer
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.example.videoeditpoc.databinding.ActivityInsertGraphicBinding
import com.google.common.collect.ImmutableList
import com.vanniktech.emoji.EmojiPopup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream


class InsertGraphicActivity : AppCompatActivity() {
    lateinit var binding: ActivityInsertGraphicBinding
    private var input_video_uri_ffmpeg: String? = null
    private var input_video_uri_media: String? = null
    val handler = Handler(Looper.getMainLooper())
    private var gifUri: Uri? = null
    var filePath: String? = null
    lateinit var exoPlayer: ExoPlayer
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInsertGraphicBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.selectVideoBtn.setOnClickListener {
            handler.removeCallbacksAndMessages(null)
            selectVideoLauncherUsingFfmpeg.launch("video/*")
        }

        binding.insertEmojiBtn.setOnClickListener {
            if (input_video_uri_media != null) {
                insertEmoji()
            } else {
                Toast.makeText(this, "Please upload the video", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
        }

        val emojiPopup =
            EmojiPopup.Builder.fromRootView(binding.insertGraphicRl).build(binding.etEmoji)

        binding.btnEmojis.setOnClickListener {
            emojiPopup.toggle()
        }
        exoPlayer = ExoPlayer.Builder(this).build()
        binding.playerView.player = exoPlayer
        binding.saveVideo.setOnClickListener {
            if (input_video_uri_ffmpeg != null) {
                //passing filename
                saveVideoLauncher.launch("result")
            } else Toast.makeText(
                this@InsertGraphicActivity, "Please upload video", Toast.LENGTH_LONG
            ).show()
        }

        binding.insertGraphicBtn.setOnClickListener {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
                    this, Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                insertGif()
            } else {
                ActivityCompat.requestPermissions(
                    this, arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ), 1
                )
            }
        }

        binding.insertPictureActivityBtn.setOnClickListener {
            startActivity(Intent(this, InsertPictureActivity::class.java))
        }

    }

    @OptIn(UnstableApi::class)
    val emojiLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK && result.data == null) {
                return@registerForActivityResult
            }
            input_video_uri_media = result.data!!.data.toString()
            Log.d("gamedia", "input_video_uri_media: $input_video_uri_media")
            val prefs = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
            val editor = prefs.edit()
            editor.putString("inputVideoUriMedia", input_video_uri_media)
            editor.apply()
            val mediaItem = MediaItem.fromUri(input_video_uri_media!!.toUri())
            Log.d("gamedia", "mediaItem: $mediaItem")

            Toast.makeText(this, "mediaItemLoadSuccess: $mediaItem", Toast.LENGTH_SHORT).show()

            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.play()
            exoPlayer.setVideoEffects(createVideoEffects())
            createTransformation(mediaItem)

        }


    @OptIn(UnstableApi::class)
    private fun createOverlayEffect(): OverlayEffect? {
        val overLaysBuilder: ImmutableList.Builder<TextureOverlay> = ImmutableList.builder()
        val overlaySettings = OverlaySettings.Builder().build()

        val getEmoji = binding.etEmoji.text
        val overlayEmoji = SpannableString(getEmoji)
        overlayEmoji.setSpan(
            ForegroundColorSpan(Color.BLUE),
            0,
            overlayEmoji.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        val emojiTextureOverlay: TextureOverlay =
            TextOverlay.createStaticTextOverlay(overlayEmoji, overlaySettings)
        overLaysBuilder.add(emojiTextureOverlay)

        val overlays: ImmutableList<TextureOverlay> = overLaysBuilder.build()
        return if (overlays.isEmpty()) null else OverlayEffect(overlays)
    }


    private fun createVideoEffects(): ImmutableList<Effect> {
        val effects = ImmutableList.Builder<Effect>()
        val overlayEffect: OverlayEffect = createOverlayEffect()!!
        effects.add(overlayEffect)
        return effects.build()
    }

    @OptIn(UnstableApi::class)
    private fun createTransformation(mediaItem: MediaItem) {
        val request = TransformationRequest.Builder().setVideoMimeType(MimeTypes.VIDEO_H264).build()
        val transformerListener: Transformer.Listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, result: ExportResult) {
                Log.d("vcas", "success")
                Toast.makeText(this@InsertGraphicActivity, "success: $result", Toast.LENGTH_SHORT)
                    .show()
            }

            override fun onError(
                composition: Composition, result: ExportResult, exception: ExportException
            ) {
                Log.d("vcae", "fail")
            }
        }
        val transformer = Transformer.Builder(this).setTransformationRequest(request)
            .addListener(transformerListener).build()
        val filePath: String =
            createExternalCacheFile("transformer.mp4").absolutePath
        transformer.start(mediaItem, filePath)
    }

    private fun insertEmoji() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "video/*"
        emojiLauncher.launch(intent)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            1 -> {
                if (grantResults.isNotEmpty()) {
                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
                        var isPermissionsGranted = false
                        if (grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                            isPermissionsGranted = true
                        }

                        if (isPermissionsGranted) {

                            insertGif()
                            Toast.makeText(this, "Permission Granted", Toast.LENGTH_LONG).show()
                        } else Toast.makeText(this, "Permission denied", Toast.LENGTH_LONG).show()
                    } else {

                        if (input_video_uri_ffmpeg != null) {
                            insertGif()
                        } else {
                            Toast.makeText(
                                this@InsertGraphicActivity, "Please upload video", Toast.LENGTH_LONG
                            ).show()
                            return
                        }
                    }
                } else {
                    Toast.makeText(this, "Permission denied", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun insertGif() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/gif"
        gifLauncher.launch(intent)
    }

    private fun getTime(seconds: Int): String {
        val hr = seconds / 3600
        val rem = seconds % 3600
        val mn = rem / 60
        val sec = rem % 60
        return String.format("%02d", hr) + ":" + String.format(
            "%02d", mn
        ) + ":" + String.format("%02d", sec)
    }

    private fun executeFfmpegCommand(exe: String, filePath: String) {

        //creating the progress dialog
        val progressDialog = ProgressDialog(this@InsertGraphicActivity)
        progressDialog.setCancelable(false)
        progressDialog.setCanceledOnTouchOutside(false)
        progressDialog.show()

        /*
            Here, we have used he Async task to execute our query because if we use the regular method the progress dialog
            won't be visible. This happens because the regular method and progress dialog uses the same thread to execute
            and as a result only one is a allowed to work at a time.
            By using we Async task we create a different thread which resolves the issue.
         */
        FFmpegKit.executeAsync(exe, { session ->
            val returnCode = session.returnCode
            lifecycleScope.launch(Dispatchers.Main) {
                if (returnCode.isValueSuccess) {
                    //after successful execution of ffmpeg command,
                    //again set up the video Uri in VideoView
                    Log.d("ffmpeg", "execFilePath: $filePath")
                    //change the video_url to filePath, so that we could do more manipulations in the
                    //resultant video. By this we can apply as many effects as we want in a single video.
                    //Actually there are multiple videos being formed in storage but while using app it
                    //feels like we are doing manipulations in only one video
                    input_video_uri_ffmpeg = filePath
                    Log.d("ffmpeg", "execInputVideoUri: $input_video_uri_ffmpeg")
                    //play the result video in VideoView
                    progressDialog.dismiss()
                    Toast.makeText(this@InsertGraphicActivity, "Filter Applied", Toast.LENGTH_SHORT)
                        .show()
                } else {
                    progressDialog.dismiss()
                    Log.d("TAG", session.allLogsAsString)
                    Toast.makeText(
                        this@InsertGraphicActivity, "Something Went Wrong!", Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }, { log ->
            lifecycleScope.launch(Dispatchers.Main) {
                progressDialog.setMessage("Applying Filter..${log.message}")
            }
        }) { statistics -> Log.d("STATS", statistics.toString()) }
    }

    private var gifLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                gifUri = result.data!!.data
                Log.d("gifuri", "gifUri: $gifUri")
                if (gifUri != null && "content" == gifUri!!.scheme) {
                    val cursor = this.contentResolver.query(
                        gifUri!!, arrayOf(MediaStore.Images.ImageColumns.DATA), null, null, null
                    )
                    cursor!!.moveToFirst()
                    filePath = cursor.getString(0)
                    Log.d("filePathScheme", "filePath: $filePath")
                    cursor.close()
                } else {
                    filePath = gifUri!!.path
                    Log.d("filePathwoScheme", "filePathwosc: $filePath ")
                }
                Log.d("", "Chosen path = $filePath")
                Log.d("gifma", "gif : $gifUri")
//                val folder = cacheDir
//                val file = File(folder, System.currentTimeMillis().toString() + ".mp4")

                val newFilePath: String = createExternalCacheFile(
                    System.currentTimeMillis().toString() + ".mp4"
                ).absolutePath
                val command =
                    "-y -i $input_video_uri_ffmpeg -stream_loop -1 -i ${Uri.fromFile(File(filePath!!))} -filter_complex [0]overlay=x=0:y=0:shortest=1[out] -map [out] -map 0:a? $newFilePath"
                executeFfmpegCommand(command, newFilePath)
            }
        }


    private val saveVideoLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("video/mp4")) {
            it?.let {
                val out = contentResolver.openOutputStream(it)
                val ip: InputStream = FileInputStream(input_video_uri_ffmpeg)

                //com.google.common.io.ByteStreams, also provides a direct method to copy
                // all bytes from the input stream to the output stream. Does not close or
                // flush either stream.
                // copy(ip,out!!)

                out?.let {
                    val buffer = ByteArray(1024)
                    var read: Int
                    while (ip.read(buffer).also { read = it } != -1) {
                        out.write(buffer, 0, read)
                    }
                    ip.close()
                    // write the output file (You have now copied the file)
                    out.flush()
                    out.close()
                }
            }
        }


    private val selectVideoLauncherUsingFfmpeg =
        registerForActivityResult(ActivityResultContracts.GetContent()) {
            it?.let {
                input_video_uri_ffmpeg = FFmpegKitConfig.getSafParameterForRead(this, it)
                val prefs = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
                val editor = prefs.edit()
                editor.putString("inputVideoUri", input_video_uri_ffmpeg)
                editor.apply()
                Toast.makeText(
                    this, "video loaded successfully: $input_video_uri_ffmpeg", Toast.LENGTH_SHORT
                ).show()
            }
        }

    @Throws(IOException::class)
    private fun createExternalCacheFile(fileName: String): File {
        val file = File(externalCacheDir, fileName)
        check(!(file.exists() && !file.delete())) { "Could not delete the previous export output file" }
        check(file.createNewFile()) { "Could not create the export output file" }
        return file
    }

    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
        input_video_uri_ffmpeg = prefs.getString("inputVideoUri", null)
        input_video_uri_media = prefs.getString("inputVideoUriMedia", null)
        Log.d("resumeita", "videoUri: $input_video_uri_ffmpeg")
        Log.d("resumeitamedia", "videoMediaUri: $input_video_uri_media")
    }
}