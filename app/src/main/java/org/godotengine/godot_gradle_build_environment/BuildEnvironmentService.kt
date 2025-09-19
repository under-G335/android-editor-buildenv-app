package org.godotengine.godot_gradle_build_environment

import android.app.Service
import android.content.Context
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

        val debianRootfs = File(filesDir, "rootfs/alpine-android-35-jdk17")
    }

    private inner class IncomingHandler(
        private val parent: BuildEnvironmentService,
    ): Handler() {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MSG_EXECUTE_COMMAND -> {
                    Log.i("DRS", "recieved MSG_EXECUTE_COMMAND")
                    val id = msg.arg1
                    val data = msg.data
                    val path = data.getString("path")
                    //val workDir = data.getString("workDir")
                    val args = data.getStringArrayList("args")

                    if (path != null && args != null) {
                        args.add(0, path)
                        val result = this@BuildEnvironmentService.executeCommand(args)
                        Log.i("DRS", "We execed a command on request and got: $result")

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
                else -> super.handleMessage(msg)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        // @todo Is this a good place to validate if it's Godot connecting?
        Toast.makeText(applicationContext, "binding", Toast.LENGTH_SHORT).show()
        mMessager = Messenger(IncomingHandler(this))
        return mMessager.binder
    }

    private data class CommandResult(val exitCode: Int, val stdout: String, val stderr: String)

    private fun executeCommand(args: List<String>): CommandResult {
        val libDir = applicationInfo.nativeLibraryDir
        val proot = File(libDir, "libproot.so").absolutePath
        val rootfs = File(filesDir, "rootfs/alpine-android-35-jdk17").absolutePath

        var prootTmpDir = File(filesDir, "proot-tmp")
        prootTmpDir.mkdirs()

        val env = HashMap(System.getenv())
        env["PROOT_TMP_DIR"] = prootTmpDir.absolutePath
        env["PROOT_LOADER"] = File(libDir, "libproot-loader.so").absolutePath
        env["PROOT_LOADER_32"] = File(libDir, "libproot-loader32.so").absolutePath

        val cmd = listOf(
            proot,
            // Do we really want `-0`?
            //"-0",
            // Should we do capital -R?
            "-r", rootfs,
            "-w", "/",
            //"-b", "/dev",
            //"-b", "/proc",
            //"-b", "/sys",
            //"/usr/bin/env", "-i",
            //"HOME=/root",
            //"PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
        ) + args

        Log.i(TAG, "Cmd: " + cmd.toString())

        val process = ProcessBuilder(cmd).apply {
            directory(filesDir)
            environment().putAll(env)
            //redirectErrorStream()
        }.start()
        //val process = pb.start()

        val output = process.inputStream.bufferedReader().use { it.readText() }
        var error = process.errorStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()

        Log.i(TAG, "Output: " + output)
        Log.i(TAG, "Error: " + error)
        Log.i(TAG, "ExitCode: " + exitCode.toString())

        return CommandResult(exitCode, output, error)
    }

}
