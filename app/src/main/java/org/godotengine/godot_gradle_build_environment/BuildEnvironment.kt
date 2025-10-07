package org.godotengine.godot_gradle_build_environment

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class BuildEnvironment(
    private val context: Context,
    private val rootfs: String,
) {

    companion object {
        private const val TAG = "BuildEnvironment"
        private const val STDOUT_TAG = "BuildEnvironment-Stdout"
        private const val STDERR_TAG = "BuildEnvironment-Stderr"

        public const val OUTPUT_INFO = 0;
        public const val OUTPUT_STDOUT = 1;
        public const val OUTPUT_STDERR = 2;
    }

    private val defaultEnv: List<String>
    private var currentProjectPath: String = ""
    private var currentProcess: Process? = null

    init {
        defaultEnv = try {
            File(rootfs, "env").readLines()
        } catch (e: IOException) {
            Log.i(TAG, "Unable to read default environment from $rootfs/env: $e")
            emptyList<String>()
        }
    }

    private fun logAndCaptureStream(reader: BufferedReader, handler: (String) -> Unit): Thread {
        return Thread {
            reader.useLines { lines ->
                lines.forEach { line ->
                    handler(line)
                }
            }
        }
    }

    fun executeCommand(
        path: String,
        args: List<String>,
        binds: List<String>,
        workDir: String,
        outputHandler: (Int, String) -> Unit,
    ): Int {
        if (currentProcess != null) {
            Log.e(TAG, "Cannot run a new process when there's already one running")
            return 255
        }

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

        //val qemu = File(libDir, "libqemu-x86_64.so")

        val cmd = buildList {
            addAll(
                listOf(
                    proot,
                    //"-0",
                    "-R", rootfs,
                    "-w", workDir,
                    //"-q", qemu.absolutePath,
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

        currentProcess = ProcessBuilder(cmd).apply {
            directory(context.filesDir)
            environment().putAll(env)
        }.start()

        val stdoutThread = logAndCaptureStream(BufferedReader(InputStreamReader(currentProcess?.inputStream)), { line ->
            Log.i(STDOUT_TAG, line)
            outputHandler(OUTPUT_STDOUT, line)
        })
        val stderrThread = logAndCaptureStream(BufferedReader(InputStreamReader(currentProcess?.errorStream)), { line ->
            Log.i(STDERR_TAG, line)
            outputHandler(OUTPUT_STDERR, line)
        })

        stdoutThread.start()
        stderrThread.start()

        stdoutThread.join()
        stderrThread.join()

        val exitCode = currentProcess?.waitFor() ?: 255
        Log.i(TAG, "ExitCode: " + exitCode.toString())

        currentProcess = null
        return exitCode
    }

    private fun changeProject(projectPath: String, gradleBuildDir: String): File {
        val tmpDir = File(rootfs,"tmp/build")

        if (currentProjectPath == projectPath) {
            return tmpDir;
        }
        currentProjectPath = ""

        if (tmpDir.exists()) {
            tmpDir.deleteRecursively()
        }
        FileUtils.tryCopyDirectory(File(projectPath, gradleBuildDir), tmpDir)

        currentProjectPath = projectPath
        return tmpDir
    }

    private fun findAapt2Jars(root: File): List<File> {
        val regex = Regex("""aapt2-.*-linux\.jar""")
        return root.walkTopDown()
            .filter { it.isFile && regex.matches(it.name) }
            .toList()
    }

    private fun executeGradleInternal(gradleArgs: List<String>, workDir: File, outputHandler: (Int, String) -> Unit): Int {
        val gradleCmd = buildString {
            append("bash gradlew ")
            append(gradleArgs.joinToString(" ") { "\"$it\""})
            if ("--no-daemon" !in gradleArgs) {
                append(" --no-daemon")
            }
        }

        val path = "/bin/bash"
        val args = listOf(
            "-c",
            gradleCmd,
        )
        val binds = listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).absolutePath,
        )

        return executeCommand(path, args, binds, workDir.absolutePath, outputHandler)
    }

    fun executeGradle(gradleArgs: List<String>, projectPath: String, gradleBuildDir: String, outputHandler: (Int, String) -> Unit): Int {
        val tmpDir = changeProject(projectPath, gradleBuildDir)
        val workDir = tmpDir.relativeTo(File(rootfs))

        // @todo This runs gradle in place - I think we could probably hack proot until it works.
        //val tmpDir = File(projectPath, gradleBuildDir)
        //val workDir = tmpDir

        val stderrBuilder = StringBuilder()

        var result = executeGradleInternal(gradleArgs, workDir, { type, line ->
            if (type == OUTPUT_STDERR) {
                synchronized(stderrBuilder) {
                    stderrBuilder.appendLine(line)
                }
            }
            outputHandler(type, line)
        })

        // Detect if we hit the AAPT2 issue.
        val stderr = stderrBuilder.toString()
        if (stderr.contains("BUILD FAILED") && stderr.contains(Regex("""AAPT2 aapt2.*Daemon startup failed"""))) {
            outputHandler(OUTPUT_INFO, "> Detected AAPT2 issue - attempting to patch the JAR files...")
            // Update the JAR files to include the aapt2 that is bundled in the rootfs.
            findAapt2Jars(tmpDir).forEach { jarFile ->
                Log.d(TAG, "Found jar file: ${jarFile.absolutePath}")
                var jarFileRelative = jarFile.relativeTo(File(rootfs))
                var args = listOf(
                    "-c",
                    "jar -u -f /${jarFileRelative.path} -C $(dirname $(which aapt2)) aapt2",
                )
                val jarUpdateResult = executeCommand("/bin/bash", args, ArrayList<String>(), workDir.absolutePath, outputHandler)
                if (jarUpdateResult != 0) {
                    // If this failed, then there's not much else we can do.
                    return jarUpdateResult;
                }
            }

            // Now, try the running Gradle again!
            outputHandler(OUTPUT_INFO, "> Retrying Gradle build...")
            result = executeGradleInternal(gradleArgs, workDir, outputHandler)
        }

        return result
    }

    fun killCurrentProcess() {
        if (currentProcess != null) {
            currentProcess?.destroy()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                currentProcess?.waitFor(500, TimeUnit.MILLISECONDS)
                currentProcess?.destroyForcibly()
            }
            currentProcess = null
        }
    }

    fun cleanProject(projectPath: String, gradleBuildDir: String) {
        // @todo Copy everything in the tmp dir back to the original location
        // @todo Delete the temp dir
    }

}