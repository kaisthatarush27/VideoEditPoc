package com.example.videoeditpoc

import android.Manifest
import android.app.Activity
import android.app.ProgressDialog
import android.content.Context
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
import androidx.media3.common.MimeTypes
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.example.videoeditpoc.databinding.ActivityInsertPictureBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import kotlin.math.abs

class InsertPictureActivity : AppCompatActivity() {
    lateinit var binding: ActivityInsertPictureBinding
    private var input_video_uri_ffmpeg: String? = null
    val handler = Handler(Looper.getMainLooper())
    private var imageUri: Uri? = null
    var imageFilePath: String? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInsertPictureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.selectVideoBtn.setOnClickListener {
            handler.removeCallbacksAndMessages(null)
            selectVideoLauncherUsingFfmpeg.launch("video/*")
        }

        binding.insertEmojiActBtn.setOnClickListener {
            startActivity(Intent(this, InsertEmojiActivity::class.java))
        }

        binding.insertPictureBtn.setOnClickListener {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
                    this, Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                insertImage()
            } else {
                ActivityCompat.requestPermissions(
                    this, arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ), 1
                )
            }
        }

        binding.saveVideo.setOnClickListener {
            if (input_video_uri_ffmpeg != null) {
                //passing filename
                saveVideoLauncher.launch("VID-${System.currentTimeMillis() / 1000}")
            } else Toast.makeText(
                this@InsertPictureActivity, "Please upload video", Toast.LENGTH_LONG
            ).show()
        }

        binding.reverseVideoBtnBtn.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
    }

    private var imageLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                imageUri = result.data!!.data
                Log.d("imageUri", "imageUri: $imageUri")
                if (imageUri != null && "content" == imageUri!!.scheme) {
                    val cursor = this.contentResolver.query(
                        imageUri!!, arrayOf(MediaStore.Images.ImageColumns.DATA), null, null, null
                    )
                    cursor!!.moveToFirst()
                    imageFilePath = cursor.getString(0)
                    Log.d("imageFilePathScheme", "imageFilePath: $imageFilePath")
                    cursor.close()
                } else {
                    imageFilePath = imageUri!!.path
                    Log.d("imageFilePathwoScheme", "imageFilePathwosc: $imageFilePath ")
                }
                Log.d("", "Chosen path = $imageFilePath")
                Log.d("imagema", "image : $imageUri")
//                val folder = cacheDir
//                val file = File(folder, System.currentTimeMillis().toString() + ".mp4")

                val newFilePath: String = createExternalCacheFile(
                    System.currentTimeMillis().toString() + ".mp4"
                ).absolutePath
                val command =
                    "-y -i $input_video_uri_ffmpeg -i ${
                        Uri.fromFile(
                            File(
                                imageFilePath!!
                            )
                        )
                    } -filter_complex \"[0:v][1:v] overlay=25:25:enable='between(t,0,20)'\" -pix_fmt yuv420p -c:a copy $newFilePath"
                executeFfmpegCommand(command, newFilePath)
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

                            insertImage()
                            Toast.makeText(this, "Permission Granted", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(this, "Permission denied", Toast.LENGTH_LONG).show()
                        }
                    } else {

                        if (input_video_uri_ffmpeg != null) {
                            insertImage()
                        } else {
                            Toast.makeText(
                                this@InsertPictureActivity, "Please upload video", Toast.LENGTH_LONG
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

    private val selectVideoLauncherUsingFfmpeg =
        registerForActivityResult(ActivityResultContracts.GetContent()) {
            it?.let {
                input_video_uri_ffmpeg = FFmpegKitConfig.getSafParameterForRead(this, it)
//                val prefs = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
//                val editor = prefs.edit()
//                editor.putString("inputVideoUri", input_video_uri_ffmpeg)
//                editor.apply()
                Toast.makeText(
                    this,
                    "video loaded successfully: $input_video_uri_ffmpeg",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

//    override fun onResume() {
//        super.onResume()
//        val prefs = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
//        input_video_uri_ffmpeg = prefs.getString("inputVideoUri", null)
//        Log.d("resumeita", "videoUri: $input_video_uri_ffmpeg")
//    }

    private val saveVideoLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument(MimeTypes.VIDEO_H264)) {
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
//                    out.flush()
                    out.close()
                }
            }
        }


    private fun insertImage() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        imageLauncher.launch(intent)
    }

    private fun executeFfmpegCommand(exe: String, filePath: String) {

        //creating the progress dialog
        val progressDialog = ProgressDialog(this@InsertPictureActivity)
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
                    //change the video_url to filePath, so that we could do more manipulations in the
                    //resultant video. By this we can apply as many effects as we want in a single video.
                    //Actually there are multiple videos being formed in storage but while using app it
                    //feels like we are doing manipulations in only one video
                    input_video_uri_ffmpeg = filePath
                    //play the result video in VideoView
                    progressDialog.dismiss()
                    Toast.makeText(this@InsertPictureActivity, "Filter Applied", Toast.LENGTH_SHORT)
                        .show()
                } else {
                    progressDialog.dismiss()
                    Log.d("TAG", session.allLogsAsString)
                    Toast.makeText(
                        this@InsertPictureActivity, "Something Went Wrong!", Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }, { log ->
            lifecycleScope.launch(Dispatchers.Main) {
                progressDialog.setMessage("Applying Filter..${log.message}")
            }
        }) { statistics ->
            Log.d("STATS", statistics.toString())

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