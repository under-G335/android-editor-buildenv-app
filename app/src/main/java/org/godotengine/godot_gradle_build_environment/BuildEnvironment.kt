package org.godotengine.godot_gradle_build_environment

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException

class BuildEnvironment(
    private val context: Context,
    private val rootfs: String,
) {

    companion object {
        private const val TAG = "BuildEnvironment"
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

        val cmd = buildList {
            addAll(
                listOf(
                    proot,
                    "-R", rootfs,
                    "-w", workDir,
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
            add(path)
            addAll(args)
        }

        Log.i(TAG, "Cmd: " + cmd.toString())

        val process = ProcessBuilder(cmd).apply {
            directory(context.filesDir)
            environment().putAll(env)
        }.start()

        val exitCode = process.waitFor()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val error = process.errorStream.bufferedReader().use { it.readText() }

        Log.i(TAG, "Output: " + output)
        Log.i(TAG, "Error: " + error)
        Log.i(TAG, "ExitCode: " + exitCode.toString())

        return CommandResult(exitCode, output, error)
    }

    fun executeGradle(arguments: List<String>, projectPath: String, buildDir: String) {

    }

}