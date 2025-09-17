package org.godotengine.godot_gradle_build_environment

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.util.Log
import android.widget.Toast
import java.io.File

private const val MSG_EXECUTE_COMMAND = 1
private const val MSG_SAY_HELLO = 2

class BuildEnvironmentService : Service() {

    private lateinit var mMessager: Messenger

    private lateinit var mEmbeddedLinux: EmbeddedLinux

    override fun onCreate() {
        super.onCreate()

        val debianRootfs = File(filesDir, "rootfs/alpine-android-35-jdk17")
        mEmbeddedLinux = EmbeddedLinux(this, debianRootfs.absolutePath)
    }

    internal class IncomingHandler(
        private val parent: BuildEnvironmentService,
        private val applicationContext: Context = parent.applicationContext
    ): Handler() {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MSG_EXECUTE_COMMAND -> {
                    Log.i("DRS", "recieved MSG_EXECUTE_COMMAND")
                    val data = msg.data
                    val path = data.getString("path")
                    //val workDir = data.getString("workDir")
                    val args = data.getStringArrayList("args")

                    if (path != null && args != null) {
                        args.add(0, path)
                        val result = parent.mEmbeddedLinux.exec(args)
                        Log.i("DRS", "We execed a command on request and got: $result")
                    }
                }
                MSG_SAY_HELLO ->
                    Toast.makeText(applicationContext, "hello!", Toast.LENGTH_SHORT).show()
                else -> super.handleMessage(msg)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        Toast.makeText(applicationContext, "binding", Toast.LENGTH_SHORT).show()
        mMessager = Messenger(IncomingHandler(this))
        return mMessager.binder
    }

}
