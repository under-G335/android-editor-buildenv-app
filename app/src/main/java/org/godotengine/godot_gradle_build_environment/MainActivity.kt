package org.godotengine.godot_gradle_build_environment

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import org.godotengine.godot_gradle_build_environment.ui.theme.GodotGradleBuildEnvironmentTheme
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

class MainActivity : ComponentActivity() {

    companion object {
        private const val REQUEST_MANAGE_EXTERNAL_STORAGE_REQ_CODE = 2002

        private const val TAG = "GradleBuildEnvironment"

    }

    private fun tryCopyFile(source: File, dest: File): Boolean {
        try {
            FileInputStream(source).use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to copy ${source.absolutePath} to ${dest.absolutePath}: ${e.message}")
            return false
        }
        return true
    }

    private fun tryCopyDirectory(sourceDir: File, destDir: File): Boolean {
        if (!sourceDir.isDirectory) {
            Log.e(TAG, "Source directory ${sourceDir.absolutePath} not found")
            return false
        }

        sourceDir.walkTopDown().forEach { source ->
            val relativePath = source.relativeTo(sourceDir)
            val target = File(destDir, relativePath.path)

            try {
                if (source.isDirectory) {
                    if (!target.exists()) {
                        target.mkdirs()
                    }
                } else {
                    if (!tryCopyFile(source, target)) {
                        return false
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Failed to copy ${source.absolutePath} -> ${target.absolutePath}: ${e.message}")
                return false
            }
        }

        return true
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:$packageName")
                    startActivityForResult(intent, REQUEST_MANAGE_EXTERNAL_STORAGE_REQ_CODE)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivityForResult(intent, REQUEST_MANAGE_EXTERNAL_STORAGE_REQ_CODE)
                }
            }
        }

        // Extract the rootfs
        val debianRootfs = File(filesDir, "rootfs/alpine-android-35-jdk17")
        if (!debianRootfs.exists()) {
            debianRootfs.mkdirs()
            TarXzExtractor.extractAssetTarXz(this, "linux-rootfs/alpine-android-35-jdk17.tar.xz", debianRootfs)

            // Docker doesn't let us write resolv.conf and so we take this extra unpacking step.
            val resolveConf = File(debianRootfs, "etc/resolv.conf")
            val resolveConfOverride = File(debianRootfs, "etc/resolv.conf.override")
            if (resolveConfOverride.exists()) {
                if (tryCopyFile(resolveConfOverride, resolveConf)) {
                    resolveConfOverride.delete()
                }
            }
        }

        // Debug code:
        val libDir = applicationInfo.nativeLibraryDir
        Log.i("Check", "nativeLibraryDir = $libDir")
        File(libDir).listFiles()?.forEach { f ->
            Log.i("Check", " - ${f.name} size=${f.length()} exec=${f.canExecute()}")
        }

        val buildSource = File("/storage/emulated/0/Documents/multitouch-cubes-demo/android/build")
        val buildDest = File(debianRootfs, "/tmp/build")
        tryCopyDirectory(buildSource, buildDest)
        val gradlewDest = File(buildDest, "gradlew")
        gradlewDest.setExecutable(true, false)

        // DEBUG!
        val buildEnv = BuildEnvironment(this, debianRootfs.absolutePath)
        val binds = listOf(
            "/storage/emulated/0/Documents/multitouch-cubes-demo/",
        )
        val args = listOf(
            //"/bin/bash",
            "-c",
            //"bash gradlew tasks",
            //"rm -rf /tmp/ttt && cp -r /storage/emulated/0/Documents/multitouch-cubes-demo/android/build /tmp/ttt",
            //"cd /tmp/ttt && bash gradlew tasks",
            "bash gradlew tasks --no-daemon",
            //"ping -c 2 services.gradle.org"
            //"curl http://1.1.1.1/"
            //"cat /etc/resolv.conf.override"
            //"echo nameserver 8.8.8.8 > /etc/resolv.conf && cat /etc/resolv.conf"





            //"set",
            //"echo 'hi' > drs",
            //"sh gradlew tasks",
            //"echo \$HOME",
            //"java",
            //"/tmp/gradlew tasks",
        )
        buildEnv.executeCommand(
            //"/usr/bin/env",
            "/bin/bash",
            args,
            binds,
            "/tmp/build",
            //"/storage/emulated/0/Documents/multitouch-cubes-demo/android/build",
        )

        setContent {
            GodotGradleBuildEnvironmentTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_MANAGE_EXTERNAL_STORAGE_REQ_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    Toast.makeText(this, "Permission granted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Permission NOT granted", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    GodotGradleBuildEnvironmentTheme {
        Greeting("Android")
    }
}