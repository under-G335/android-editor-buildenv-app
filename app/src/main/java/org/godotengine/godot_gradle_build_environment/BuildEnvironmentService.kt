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

private const val MSG_EXECUTE_COMMAND = 1
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
                MSG_EXECUTE_COMMAND -> {
                    Log.i("DRS", "recieved MSG_EXECUTE_COMMAND")
                    this@BuildEnvironmentService.executeCommand(msg)
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

    private fun executeCommand(msg: Message) {
        val id = msg.arg1

        val data = msg.data
        val path = data.getString("path")
        val workDir = data.getString("workDir")
        val args = data.getStringArrayList("args") ?: ArrayList<String>()
        val binds = data.getStringArrayList("binds") ?: ArrayList<String>()

        val libDir = applicationInfo.nativeLibraryDir
        val proot = File(libDir, "libproot.so").absolutePath
        val rootfs = File(filesDir, "rootfs/alpine-android-35-jdk17").absolutePath

        val prootTmpDir = File(filesDir, "proot-tmp")
        prootTmpDir.mkdirs()

        val env = HashMap(System.getenv())
        env["PROOT_TMP_DIR"] = prootTmpDir.absolutePath
        env["PROOT_LOADER"] = File(libDir, "libproot-loader.so").absolutePath
        env["PROOT_LOADER_32"] = File(libDir, "libproot-loader32.so").absolutePath

        val cmd = buildList {
            addAll(listOf(
                proot,
                "-R", rootfs,
                "-w", workDir,
            ))
            for (bind in binds) {
                addAll(listOf("-b", bind))
            }
            /*
            addAll(listOf(
                "/usr/bin/env", "-i",
                "HOME=/root",
                "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            ))
            */
            add(path)
            addAll(args)
        }

        Log.i(TAG, "Cmd: " + cmd.toString())

        val process = ProcessBuilder(cmd).apply {
            directory(filesDir)
            environment().putAll(env)
        }.start()

        val output = process.inputStream.bufferedReader().use { it.readText() }
        val error = process.errorStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()

        Log.i(TAG, "Output: " + output)
        Log.i(TAG, "Error: " + error)
        Log.i(TAG, "ExitCode: " + exitCode.toString())

        val reply = Message.obtain(null, MSG_COMMAND_RESULT, id, 0)
        val replyData = Bundle()
        replyData.putInt("exitCode", exitCode)
        replyData.putString("stdout", output)
        replyData.putString("stderr", error)
        reply.data = replyData

        try {
            msg.replyTo.send(reply)
        } catch (e: RemoteException) {
            Log.e(TAG, "Error sending result to client: ${e.message}")
        }
    }

}
