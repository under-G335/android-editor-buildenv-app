package org.godotengine.godot_gradle_build_environment

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.util.Log
import android.widget.Toast
import java.io.File

private const val MSG_EXECUTE_GRADLE = 1
private const val MSG_COMMAND_RESULT = 2

class BuildEnvironmentService : Service() {

    companion object {
        private const val TAG = "BuildEnvironmentService"
    }

    private lateinit var mMessager: Messenger

    override fun onCreate() {
        super.onCreate()
    }

    private inner class IncomingHandler(): Handler() {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MSG_EXECUTE_GRADLE -> {
                    Log.i("DRS", "recieved MSG_EXECUTE_GRADLE")
                    this@BuildEnvironmentService.executeGradle(msg)
                }
                else -> super.handleMessage(msg)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        // @todo Is this a good place to validate if it's Godot connecting?
        Toast.makeText(applicationContext, "binding", Toast.LENGTH_SHORT).show()
        mMessager = Messenger(IncomingHandler())
        return mMessager.binder
    }

    private fun executeGradle(msg: Message) {
        val id = msg.arg1

        val data = msg.data
        val args = data.getStringArrayList("arguments")
        val projectPath = data.getString("project_path")
        val gradleBuildDir = data.getString("gradle_build_directory")

        var result = BuildEnvironment.CommandResult(255, "", "Invalid message")

        if (args != null && projectPath != null && gradleBuildDir != null) {
            Log.d(TAG, "Received Gradle execute request: ${args} on ${projectPath} / ${gradleBuildDir}")
            val rootfs = File(filesDir, "rootfs/alpine-android-35-jdk17").absolutePath
            val buildEnvironment = BuildEnvironment(this, rootfs)
            result = buildEnvironment.executeGradle(args, projectPath, gradleBuildDir)
        }

        val reply = Message.obtain(null, MSG_COMMAND_RESULT, id, 0)
        val replyData = Bundle()
        replyData.putInt("exitCode", result.exitCode)
        replyData.putString("stdout", result.stdout)
        replyData.putString("stderr", result.stderr)
        reply.data = replyData

        try {
            msg.replyTo.send(reply)
        } catch (e: RemoteException) {
            Log.e(TAG, "Error sending result to client: ${e.message}")
        }
    }

}
