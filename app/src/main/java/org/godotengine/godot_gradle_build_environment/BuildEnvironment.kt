package org.godotengine.godot_gradle_build_environment

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader

class BuildEnvironment(
    private val context: Context,
    private val rootfs: String,
) {

    companion object {
        private const val TAG = "BuildEnvironment"
        private const val STDOUT_TAG = "BuildEnvironment-Stdout"
        private const val STDERR_TAG = "BuildEnvironment-Stderr"
    }

    private val defaultEnv: List<String>

    init {
        defaultEnv = try {
            File(rootfs, "env").readLines()
        } catch (e: IOException) {
            Log.i(TAG, "Unable to read default environment from $rootfs/env: $e")
            emptyList<String>()
        }
    }

    private fun logAndCaptureStream(tag: String, reader: BufferedReader, output: StringBuilder) {
        Thread {
            reader.useLines { lines ->
                lines.forEach { line ->
                    Log.d(tag, line)
                    synchronized(output) {
                        output.appendLine(line)
                    }
                }
            }
        }.start()
    }

    class CommandResult(val exitCode: Int, val stdout: String, val stderr: String)

    fun executeCommand(
        path: String,
        args: List<String>,
        binds: List<String>,
        workDir: String
    ): CommandResult {
        val libDir = context.applicationInfo.nativeLibraryDir
        val proot = File(libDir, "libproot.so").absolutePath

        val prootTmpDir = File(context.filesDir, "proot-tmp")
        prootTmpDir.mkdirs()

        val env = HashMap(System.getenv())
        env["PROOT_TMP_DIR"] = prootTmpDir.absolutePath
        env["PROOT_LOADER"] = File(libDir, "libproot-loader.so").absolutePath
        env["PROOT_LOADER_32"] = File(libDir, "libproot-loader32.so").absolutePath
        //env["PROOT_NO_SECCOMP"] = "1"
        //env["PROOT_VERBOSE"] = "9"

        val cmd = buildList {
            addAll(
                listOf(
                    proot,
                    //"-0",
                    "-R", rootfs,
                    "-w", workDir,
                    // Stuff to try:
                    //"--link2symlink", "-L", "--tcsetsf2tcsets",
                )
            )
            for (bind in binds) {
                addAll(listOf("-b", bind))
            }
            addAll(
                listOf(
                    "/usr/bin/env", "-i",
                )
            )
            addAll(defaultEnv)
            //add("HOME=/root")
            add(path)
            addAll(args)
        }

        Log.i(TAG, "Cmd: " + cmd.toString())

        val process = ProcessBuilder(cmd).apply {
            directory(context.filesDir)
            environment().putAll(env)
        }.start()

        val stdout = StringBuilder()
        val stderr = StringBuilder()

        logAndCaptureStream(STDOUT_TAG, BufferedReader(InputStreamReader(process.inputStream)), stdout)
        logAndCaptureStream(STDERR_TAG, BufferedReader(InputStreamReader(process.errorStream)), stderr)

        val exitCode = process.waitFor()

        //Log.i(TAG, "Output: " + output)
        //Log.i(TAG, "Error: " + error)
        Log.i(TAG, "ExitCode: " + exitCode.toString())

        return CommandResult(exitCode, stdout.toString(), stderr.toString())
    }

    fun executeGradle(arguments: List<String>, projectPath: String, buildDir: String) {

    }

}