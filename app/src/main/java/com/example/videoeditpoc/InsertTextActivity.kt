package com.example.videoeditpoc

import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
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
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.TransformationRequest
import androidx.media3.transformer.Transformer
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.example.videoeditpoc.databinding.ActivityInsertTextBinding
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream

@UnstableApi
class InsertTextActivity : AppCompatActivity() {
    lateinit var binding: ActivityInsertTextBinding
    private var input_video_uri_ffmpeg: String? = null
    private var outputFilePath: String? = null
    private var player: ExoPlayer? = null
    val handler = Handler(Looper.getMainLooper())
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInsertTextBinding.inflate(layoutInflater)
        setContentView(binding.root)

//        binding.insertTextBtn.setOnClickListener {
//            if (input_video_uri_ffmpeg != null) {
//                insertTextUsingFfmpeg()
//            } else Toast.makeText(this, "Please upload video", Toast.LENGTH_LONG)
//                .show()
//        }

        binding.selectVideoBtn.setOnClickListener {
            handler.removeCallbacksAndMessages(null)
            if (binding.ediText.text.toString().isEmpty()) {
                Toast.makeText(this, "enter text", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            selectVideoLauncherUsingFfmpeg.launch("video/*")
        }

        binding.saveVideo.setOnClickListener {
            if (input_video_uri_ffmpeg != null) {
                //passing filename
                saveVideoLauncher.launch("VID-${System.currentTimeMillis() / 1000}")
            } else Toast.makeText(this@InsertTextActivity, "Please upload video", Toast.LENGTH_LONG)
                .show()
        }

        binding.insertGraphicActivityBtn.setOnClickListener {
            startActivity(Intent(this, InsertGraphicActivity::class.java))
        }

        initExoPLayer()

//        binding.videoView.setOnPreparedListener { mp ->
//            get the duration of the video
//            val duration = mp.duration / 1000
//            //initially set the left TextView to "00:00:00"
//            binding.textleft.text = "00:00:00"
//            //initially set the right Text-View to the video length
//            //the getTime() method returns a formatted string in hh:mm:ss
//            binding.textright.text = getTime(mp.duration / 1000)
//            //this will run he video in loop i.e. the video won't stop
//            //when it reaches its duration
//            mp.isLooping = true
//
//            //set up the initial values of binding.rangeSeekBar
//            binding.rangeSeekBar.setRangeValues(0, duration)
//            binding.rangeSeekBar.selectedMinValue = 0
//            binding.rangeSeekBar.selectedMaxValue = duration
//            binding.rangeSeekBar.isEnabled = true
//            binding.rangeSeekBar.setOnRangeSeekBarChangeListener { bar, minValue, maxValue ->
//                //we seek through the video when the user drags and adjusts the seekbar
//                binding.videoView.seekTo(minValue as Int * 1000)
//                //changing the left and right TextView according to the minValue and maxValue
//                binding.textleft.text = getTime(bar.selectedMinValue.toInt())
//                binding.textright.text = getTime(bar.selectedMaxValue.toInt())
//            }
//
//            //this method changes the right TextView every 1 second as the video is being played
//            //It works same as a time counter we see in any Video Player
//
//            handler.postDelayed(object : Runnable {
//                override fun run() {
//
//                    val time: Int = abs(duration - binding.videoView.currentPosition) / 1000
//                    binding.textleft.text = getTime(time)
//
//                    //wrapping the video, i.e. once the video reaches its length,
//                    // again starts from the current position of left seekbar point
//                    if (binding.videoView.currentPosition >= binding.rangeSeekBar.selectedMaxValue.toInt() * 1000) {
//                        binding.videoView.seekTo(binding.rangeSeekBar.selectedMinValue.toInt() * 1000)
//                    }
//                    handler.postDelayed(this, 1000)
//                }
//            }, 0)
//
//        }
    }

    private fun initExoPLayer() {
        player = ExoPlayer.Builder(this).build()
        binding.exoPlayer.player = player
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

    private val saveVideoLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("video/mp4")) {
            it?.let {
                val out = contentResolver.openOutputStream(it)
                val ip: InputStream = FileInputStream(outputFilePath)

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
                input_video_uri_ffmpeg = it.toString()
//                val prefs = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
//                val editor = prefs.edit()
//                editor.putString("inputVideoUri", input_video_uri_ffmpeg)
//                editor.apply()
//                Toast.makeText(
//                    this,
//                    "video loaded successfully: $input_video_uri_ffmpeg",
//                    Toast.LENGTH_SHORT
//                ).show()

                val mediaItem = MediaItem.fromUri(input_video_uri_ffmpeg!!)
                player!!.setMediaItem(mediaItem)
                player!!.prepare()
                player!!.play()
                player!!.setVideoEffects(createVideoEffects())
                createTransformation(mediaItem)
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
        val overlaySettings = OverlaySettings.Builder().build()

        val getUserInput = binding.ediText.text.toString()
        val spannableText = SpannableString(getUserInput)
        spannableText.setSpan(
            ForegroundColorSpan(Color.BLUE),
            0,
            spannableText.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        val overlayText: TextureOverlay =
            TextOverlay.createStaticTextOverlay(spannableText)
        overLaysBuilder.add(overlayText)


        val overlays: ImmutableList<TextureOverlay> = overLaysBuilder.build()
        return if (overlays.isEmpty()) null else OverlayEffect(overlays)
    }

    private fun createTransformation(mediaItem: MediaItem) {
        val request = TransformationRequest.Builder().setVideoMimeType(MimeTypes.VIDEO_H265).build()
        val transformerListener: Transformer.Listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, result: ExportResult) {
                Log.d("vcas", "success")
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
        outputFilePath =
            createExternalCacheFile(System.currentTimeMillis().toString() + ".mp4").absolutePath
        transformer.start(inputEditedMediaItem, outputFilePath!!)
    }

//    override fun onResume() {
//        super.onResume()
//        val prefs = getSharedPreferences("MyPrefs", MODE_PRIVATE)
//        input_video_uri_ffmpeg = prefs.getString("inputVideoUri", null)
//        Log.d("resumeita", "videoUri: $input_video_uri_ffmpeg")
//    }

//    private fun insertTextUsingFfmpeg() {
//        val getUserInput = binding.ediText.text.toString()
//        if (getUserInput.isEmpty()) {
//            Toast.makeText(this, "please enter the text", Toast.LENGTH_SHORT).show()
//            return
//        }
//        val newFilePath: String = createExternalCacheFile(
//            System.currentTimeMillis().toString() + ".mp4"
//        ).absolutePath
//        val fontFile = "/system/fonts/Roboto-Regular.ttf"
//
//        val exe =
//            "-y -i $input_video_uri_ffmpeg -vf \"drawtext=text='$getUserInput':fontfile='$fontFile':x=(main_w-text_w-10):y=(main_h-text_h-10):fontsize=100:fontcolor=black:box=1:boxcolor=white@0.5:boxborderw=5\" $newFilePath"
//        executeFfmpegCommand(exe, newFilePath)
//    }

    private fun executeFfmpegCommand(exe: String, filePath: String) {

        //creating the progress dialog
        val progressDialog = ProgressDialog(this@InsertTextActivity)
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
//                    binding.videoView.setVideoPath(filePath)
                    //change the video_url to filePath, so that we could do more manipulations in the
                    //resultant video. By this we can apply as many effects as we want in a single video.
                    //Actually there are multiple videos being formed in storage but while using app it
                    //feels like we are doing manipulations in only one video
                    input_video_uri_ffmpeg = filePath
                    //play the result video in VideoView
//                    binding.videoView.start()
                    progressDialog.dismiss()
                    Toast.makeText(this@InsertTextActivity, "Filter Applied", Toast.LENGTH_SHORT)
                        .show()
                } else {
                    progressDialog.dismiss()
                    Log.d("TAG", session.allLogsAsString)
                    Toast.makeText(
                        this@InsertTextActivity,
                        "Something Went Wrong!",
                        Toast.LENGTH_SHORT
                    )
                        .show()
                }
            }
        }, { log ->
            lifecycleScope.launch(Dispatchers.Main) {
                progressDialog.setMessage("Applying Filter..${log.message}")
            }
        }) { statistics -> Log.d("STATS", statistics.toString()) }
    }

    @Throws(IOException::class)
    private fun createExternalCacheFile(fileName: String): File {
        val file = File(externalCacheDir, fileName)
        check(!(file.exists() && !file.delete())) { "Could not delete the previous export output file" }
        check(file.createNewFile()) { "Could not create the export output file" }
        return file
    }
}