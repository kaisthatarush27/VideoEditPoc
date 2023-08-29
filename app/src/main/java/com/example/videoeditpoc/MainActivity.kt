package com.example.videoeditpoc

import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.MediaController
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.example.videoeditpoc.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream


class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var input_video_uri_ffmpeg: String? = null
    var mediaControls: MediaController? = null
    val handler = Handler(Looper.getMainLooper())
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)


//        initView()
        binding.selectVideoBtn.setOnClickListener {
            handler.removeCallbacksAndMessages(null)
            selectVideoLauncherUsingFfmpeg.launch("video/*")
        }

        binding.reverseVideoBtn.setOnClickListener {
            if (input_video_uri_ffmpeg != null) {
                reverse()
            } else Toast.makeText(this@MainActivity, "Please upload video", Toast.LENGTH_LONG)
                .show()
        }

        binding.saveVideo.setOnClickListener {
            if (input_video_uri_ffmpeg != null) {
                //passing filename
                saveVideoLauncher.launch("VID-${System.currentTimeMillis() / 1000}")
            } else Toast.makeText(this@MainActivity, "Please upload video", Toast.LENGTH_LONG)
                .show()
        }

        binding.insertTextActivityBtn.setOnClickListener {
            startActivity(Intent(this, InsertTextActivity::class.java))
        }

        binding.videoView.setOnPreparedListener { mp ->
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

        }

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

    private fun reverse() {
        val newFilePath: String = createExternalCacheFile(
            System.currentTimeMillis().toString() + ".mp4"
        ).absolutePath


        val exe = "-y -i $input_video_uri_ffmpeg -vf reverse $newFilePath"
        executeFfmpegCommand(exe, newFilePath)
    }

    private fun executeFfmpegCommand(exe: String, filePath: String) {

        //creating the progress dialog
        val progressDialog = ProgressDialog(this@MainActivity)
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
                    if (mediaControls == null) {
                        // creating an object of media controller class
                        mediaControls = MediaController(this@MainActivity)

                        // set the anchor view for the video view
                        mediaControls!!.setAnchorView(binding.videoView)
                    }

                    // set the media controller for video view
                    binding.videoView.setMediaController(mediaControls)
                    binding.videoView.setVideoPath(filePath)
                    //change the video_url to filePath, so that we could do more manipulations in the
                    //resultant video. By this we can apply as many effects as we want in a single video.
                    //Actually there are multiple videos being formed in storage but while using app it
                    //feels like we are doing manipulations in only one video
                    input_video_uri_ffmpeg = filePath
                    //play the result video in VideoView
                    binding.videoView.start()
                    progressDialog.dismiss()
                    Toast.makeText(this@MainActivity, "Filter Applied", Toast.LENGTH_SHORT).show()
                } else {
                    progressDialog.dismiss()
                    Log.d("TAG", session.allLogsAsString)
                    Toast.makeText(this@MainActivity, "Something Went Wrong!", Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }, { log ->
            lifecycleScope.launch(Dispatchers.Main) {
                progressDialog.setMessage("Applying Filter..${log.message}")
            }
        }) { statistics -> Log.d("STATS", statistics.toString()) }
    }

    private val selectVideoLauncherUsingFfmpeg =
        registerForActivityResult(ActivityResultContracts.GetContent()) {
            it?.let {
                input_video_uri_ffmpeg = FFmpegKitConfig.getSafParameterForRead(this, it)
                val prefs = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
                val editor = prefs.edit()
                editor.putString("inputVideoUri", input_video_uri_ffmpeg)
                editor.apply()
                if (mediaControls == null) {
                    // creating an object of media controller class
                    mediaControls = MediaController(this)

                    // set the anchor view for the video view
                    mediaControls!!.setAnchorView(binding.videoView)
                }

                // set the media controller for video view
                binding.videoView.setMediaController(mediaControls)
                binding.videoView.setVideoURI(it)

                //after successful retrieval of the video and properly setting up the retried video uri in
                //VideoView, Start the VideoView to play that video
                binding.videoView.start()
            }
        }

    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
        input_video_uri_ffmpeg = prefs.getString("inputVideoUri", null)
        Log.d("resumeita", "videoUri: $input_video_uri_ffmpeg")
    }

//    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
//        super.onActivityResult(requestCode, resultCode, data)
//
//        if (resultCode == RESULT_OK) {
//            if (requestCode == 1) {
//                if (data != null) {
//                    // get the video Uri
//
////                    exportData(data)
//                }
//            }
//        }
//    }
//
//    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
//    private fun exportData(data: Intent) {
//        selectedVideoUri = data.data.toString()
//        Log.d("mavideo", selectedVideoUri)
//        val mediaItem = MediaItem.fromUri(selectedVideoUri.toUri())
//        player.setMediaItem(mediaItem)
//        player.prepare()
//        player.play()
//
//        player.setVideoEffects(createVideoEffects())
//
////        val emojiPopup = EmojiPopup.Builder.fromRootView(binding.rootCl).build(binding.etEmoji)
////        emojiPopup.toggle()
//        createTransformation(mediaItem)
//    }
//
//    private fun extractGifFrames(context: Context, gifResourceId: Int): ArrayList<Bitmap> {
//        val gifInputStream: InputStream = context.resources.openRawResource(gifResourceId)
//        val movie = Movie.decodeStream(gifInputStream)
//
//        val frameCount = movie.duration() / 1000
//        val bitmaps = ArrayList<Bitmap>()
//
//        for (i in 0 until frameCount) {
//            val bitmap = Bitmap.createBitmap(movie.width(), movie.height(), Bitmap.Config.ARGB_8888)
//            val canvas = Canvas(bitmap)
//            movie.setTime(i * 1000)
//            movie.draw(canvas, 0f, 0f)
//            bitmaps.add(bitmap)
//        }
//
//        return bitmaps
//    }
//
////    private fun insertGif(data: Intent) {
////        selectedVideoUri = data.data.toString()
////        Log.d("mavideo", selectedVideoUri)
////        val mediaItem = MediaItem.fromUri(selectedVideoUri.toUri())
////        player.setMediaItem(mediaItem)
////        player.prepare()
////        player.play()
////
////        //fetchGif()
////
////        createTransformation(mediaItem)
////    }
//
//    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
//    private fun fetchGif() {
//
//    }
//
//    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
//    private fun createOverlayEffect(): OverlayEffect? {
//        val overLaysBuilder: ImmutableList.Builder<TextureOverlay> = ImmutableList.builder()
//        val overlaySettings = OverlaySettings.Builder().build()
//        val bitmap = BitmapFactory.decodeResource(resources, R.drawable.kia)
//        val bitmapOverlay = BitmapOverlay.createStaticBitmapOverlay(bitmap, overlaySettings)
//        val overLayText = SpannableString("tarush")
//        overLayText.setSpan(
//            ForegroundColorSpan(Color.BLUE),
//            0,
//            overLayText.length,
//            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
//        )
//
//        val overlayEmoji = SpannableString(resources.getString(R.string.emoji))
//        val emojiTextureOverlay: TextureOverlay =
//            TextOverlay.createStaticTextOverlay(overlayEmoji, overlaySettings)
//        val textureOverlay: TextureOverlay =
//            TextOverlay.createStaticTextOverlay(overLayText, overlaySettings)
////        overLaysBuilder.add(bitmapOverlay)
//        overLaysBuilder.add(textureOverlay)
////        overLaysBuilder.add(emojiTextureOverlay)
//
//        val overlays: ImmutableList<TextureOverlay> = overLaysBuilder.build()
//        return if (overlays.isEmpty()) null else OverlayEffect(overlays)
//    }
//
//
//    private fun createVideoEffects(): ImmutableList<Effect> {
//        val effects = ImmutableList.Builder<Effect>()
//        val overlayEffect: OverlayEffect = createOverlayEffect()!!
//        effects.add(overlayEffect)
//        return effects.build()
//    }
//
//    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
//    private fun createTransformation(mediaItem: MediaItem) {
//        val request = TransformationRequest.Builder().setVideoMimeType(MimeTypes.VIDEO_H264).build()
//        val transformerListener: Transformer.Listener = object : Transformer.Listener {
//            override fun onCompleted(composition: Composition, result: ExportResult) {
//                Log.d("vcas", "success")
//            }
//
//            override fun onError(
//                composition: Composition, result: ExportResult, exception: ExportException
//            ) {
//                Log.d("vcae", "fail")
//            }
//        }
//        val transformer = Transformer.Builder(this).setTransformationRequest(request)
//            .addListener(transformerListener).build()
//        val filePath: String = createExternalCacheFile("transformer-output.mp4").absolutePath
//        transformer.start(mediaItem, filePath)
//    }
//
//
//    private fun initView() {
////        binding.butSelectVid.setOnClickListener {
////            val intent = Intent()
////            intent.type = "video/*"
////            intent.action = Intent.ACTION_GET_CONTENT
////            startActivityForResult(
////                Intent.createChooser(intent, "Select Video"), 1
////            )
////        }
////        player = ExoPlayer.Builder(this).build()
////        binding.videoView.player = player
//    }
//
//    @Throws(IOException::class)
//    private fun createExternalCacheFile(fileName: String): File {
//        val file = File(externalCacheDir, fileName)
//        check(!(file.exists() && !file.delete())) { "Could not delete the previous export output file" }
//        check(file.createNewFile()) { "Could not create the export output file" }
//        return file
//    }

    @Throws(IOException::class)
    private fun createExternalCacheFile(fileName: String): File {
        val file = File(externalCacheDir, fileName)
        check(!(file.exists() && !file.delete())) { "Could not delete the previous export output file" }
        check(file.createNewFile()) { "Could not create the export output file" }
        return file
    }
}