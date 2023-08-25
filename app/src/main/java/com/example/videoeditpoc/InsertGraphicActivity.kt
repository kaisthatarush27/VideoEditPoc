package com.example.videoeditpoc

import android.Manifest
import android.app.Activity
import android.app.ProgressDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.example.videoeditpoc.databinding.ActivityInsertGraphicBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import kotlin.math.abs


class InsertGraphicActivity : AppCompatActivity() {
    lateinit var binding: ActivityInsertGraphicBinding
    private var input_video_uri_ffmpeg: String? = null
    val handler = Handler(Looper.getMainLooper())
    private var gifUri: Uri? = null
    var filePath: String? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInsertGraphicBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.selectVideoUsingFfmpeg.setOnClickListener {
            handler.removeCallbacksAndMessages(null)
            selectVideoLauncherUsingFfmpeg.launch("video/*")
        }

        binding.saveVideo.setOnClickListener {
            if (input_video_uri_ffmpeg != null) {
                //passing filename
                saveVideoLauncher.launch("result")
            } else Toast.makeText(
                this@InsertGraphicActivity, "Please upload video", Toast.LENGTH_LONG
            ).show()
        }

        binding.insertGraphic.setOnClickListener {

            if (input_video_uri_ffmpeg != null) {
                insertGif()
            } else {
                Toast.makeText(
                    this@InsertGraphicActivity, "Please upload video", Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }
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
//            if (input_video_uri_ffmpeg != null) {
//                insertGif()
//            } else Toast.makeText(
//                this@InsertGraphicActivity, "Please upload video", Toast.LENGTH_LONG
//            ).show()
        }

        binding.insertPictureActivity.setOnClickListener {
            startActivity(Intent(this, InsertPictureActivity::class.java))
        }

        binding.insertEmoji.setOnClickListener {
            if (input_video_uri_ffmpeg != null) {
                insertGifEmoji()
            } else {
                Toast.makeText(
                    this@InsertGraphicActivity, "Please upload video", Toast.LENGTH_LONG
                ).show()
            }
        }

        binding.videoView.setOnPreparedListener { mp ->
//            get the duration of the video
            val duration = mp.duration / 1000
            //initially set the left TextView to "00:00:00"
            binding.textleft.text = "00:00:00"
            //initially set the right Text-View to the video length
            //the getTime() method returns a formatted string in hh:mm:ss
            binding.textright.text = getTime(mp.duration / 1000)
            //this will run he video in loop i.e. the video won't stop
            //when it reaches its duration
            mp.isLooping = true

            //set up the initial values of binding.rangeSeekBar
            binding.rangeSeekBar.setRangeValues(0, duration)
            binding.rangeSeekBar.selectedMinValue = 0
            binding.rangeSeekBar.selectedMaxValue = duration
            binding.rangeSeekBar.isEnabled = true
            binding.rangeSeekBar.setOnRangeSeekBarChangeListener { bar, minValue, maxValue ->
                //we seek through the video when the user drags and adjusts the seekbar
                binding.videoView.seekTo(minValue as Int * 1000)
                //changing the left and right TextView according to the minValue and maxValue
                binding.textleft.text = getTime(bar.selectedMinValue.toInt())
                binding.textright.text = getTime(bar.selectedMaxValue.toInt())
            }

            //this method changes the right TextView every 1 second as the video is being played
            //It works same as a time counter we see in any Video Player

            handler.postDelayed(object : Runnable {
                override fun run() {

                    val time: Int = abs(duration - binding.videoView.currentPosition) / 1000
                    binding.textleft.text = getTime(time)

                    //wrapping the video, i.e. once the video reaches its length,
                    // again starts from the current position of left seekbar point
                    if (binding.videoView.currentPosition >= binding.rangeSeekBar.selectedMaxValue.toInt() * 1000) {
                        binding.videoView.seekTo(binding.rangeSeekBar.selectedMinValue.toInt() * 1000)
                    }
                    handler.postDelayed(this, 1000)
                }
            }, 0)

        }
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
//                        insertGif()
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
                    binding.videoView.setVideoPath(filePath)
                    //change the video_url to filePath, so that we could do more manipulations in the
                    //resultant video. By this we can apply as many effects as we want in a single video.
                    //Actually there are multiple videos being formed in storage but while using app it
                    //feels like we are doing manipulations in only one video
                    input_video_uri_ffmpeg = filePath
                    Log.d("ffmpeg", "execInputVideoUri: $input_video_uri_ffmpeg")
                    //play the result video in VideoView
                    binding.videoView.start()
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
            if (result.resultCode == Activity.RESULT_OK) {
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

    private fun getEmojiByUnicode(unicode: Int): String {
        return String(Character.toChars(unicode))
    }

    private fun insertGifEmoji() {
        val emojiString = getEmojiByUnicode(0x1F601)
        val fontFile = "/system/fonts/Roboto-Regular.ttf"
        val string = ""
        val newFilePath: String = createExternalCacheFile(
            System.currentTimeMillis().toString() + ".mp4"
        ).absolutePath
        val command =
            "-y -i $input_video_uri_ffmpeg -vf \"drawtext=text='I am happy $emojiString':fontfile='$fontFile':x=(main_w-text_w-10):y=(main_h-text_h-10):fontsize=100:fontcolor=black:box=1:boxcolor=white@0.5:boxborderw=5\" $newFilePath"
        executeFfmpegCommand(command, newFilePath)
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
                binding.videoView.setVideoURI(it)

                //after successful retrieval of the video and properly setting up the retried video uri in
                //VideoView, Start the VideoView to play that video
                binding.videoView.start()
            }
        }

    @Throws(IOException::class)
    private fun createExternalCacheFile(fileName: String): File {
        val file = File(externalCacheDir, fileName)
        check(!(file.exists() && !file.delete())) { "Could not delete the previous export output file" }
        check(file.createNewFile()) { "Could not create the export output file" }
        return file
    }
}