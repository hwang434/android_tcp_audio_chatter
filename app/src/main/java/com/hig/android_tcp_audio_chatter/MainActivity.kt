package com.hig.android_tcp_audio_chatter

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.*
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.text.format.Formatter
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.databinding.DataBindingUtil
import com.hig.android_tcp_audio_chatter.databinding.ActivityMainBinding
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    companion object {
        // Company IP
//        private val SERVER_IP = InetAddress.getByName("192.168.3.144")
        // Home IP
        private val SERVER_IP = InetAddress.getByName("192.168.3.144")
        private val SERVER_PORT = 50000
        private const val SAMPLE_INTERVAL = 50 // Milliseconds
        private const val commonEncoding = AudioFormat.ENCODING_PCM_16BIT
        private const val commonAudioSource = MediaRecorder.AudioSource.VOICE_RECOGNITION
        private val TAG: String = "로그"

        private const val INPUT_PORT_NUM = 40000
        private var isMicOn = false // Enable mic?
        private var isSpeakerOn = false
        val SAMPLE_RATE = 44100
        val intBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            commonEncoding
        ) * 2
    }
    private lateinit var binding: ActivityMainBinding
    private lateinit var serverSocket: ServerSocket

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 0)
        startServer()

        binding.buttonMainStartStream.setOnClickListener {
            startRecord()
            // audioTrack
            startTrack()
        }
    }

    private fun startServer() {
        serverSocket = ServerSocket(SERVER_PORT)
    }

    private fun startTrack() {
        Log.d(TAG,"MainActivity - startTrack() called")
        val audioTrack = if (Build.VERSION.SDK_INT >= 23) {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(commonEncoding)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(intBufferSize)
                .build()
        } else {
            @Suppress("Deprecated")
            AudioTrack(
                AudioManager.STREAM_VOICE_CALL,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_STEREO,
                commonEncoding,
                intBufferSize,
                AudioTrack.MODE_STREAM
            )
        }

        thread {
            audioTrack.play()
            val connfd = Socket(InetAddress.getByName("192.168.3.145"), SERVER_PORT)

            try {
                Log.d(TAG,"MainActivity - startTrack() try init called")
                val buffer = ByteArray(intBufferSize)
                isSpeakerOn = true
                audioTrack.play()
                val inputStream = connfd.getInputStream()

                while (isSpeakerOn) {
                    Log.d(TAG,"MainActivity - isSpeakerOn called")
                    var readSize = 0
                    readSize = inputStream.read(buffer)
                    Log.d(TAG,"MainActivity - track : inputStream : ${buffer.contentToString()}() called")
                    audioTrack.write(buffer, 0, readSize)
                }

                audioTrack.stop()
                try {
                    connfd.close()
                } catch (e: java.lang.Exception) {
                    e.printStackTrace()
                }

                isSpeakerOn = false
                audioTrack.stop()
                audioTrack.flush()
                audioTrack.release()
            } catch (e: Exception) {
                Log.w(TAG, "startSpeaker: ", e)
            }
        }
    }

    private fun startRecord() {
        Log.d(TAG,"MainActivity - startRecord() called")
        val audioRecord = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return
            }
            AudioRecord.Builder()
                .setAudioSource(commonAudioSource)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(commonEncoding)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build()
                )
                .setBufferSizeInBytes(intBufferSize)
                .build()
        } else {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_STEREO,
                commonEncoding,
                intBufferSize
            )
        }

        audioRecord.startRecording()
        thread {
            val buffer = ByteArray(intBufferSize)
            val socket = serverSocket.accept()
            val inputStream = socket.getInputStream()
            val outputStream = socket.getOutputStream()
            isMicOn = true
            Log.d(TAG,"MainActivity - isMicOn = $isMicOn")
            while (isMicOn) {
                val readSize = audioRecord.read(buffer, 0, buffer.size)
                outputStream.write(buffer)
            }

            isMicOn = false
            audioRecord.stop()
            audioRecord.release()
            serverSocket.close()
        }
    }
}