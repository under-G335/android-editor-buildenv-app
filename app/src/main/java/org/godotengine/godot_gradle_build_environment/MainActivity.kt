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

class MainActivity : ComponentActivity() {
    private val REQUEST_MANAGE_EXTERNAL_STORAGE_REQ_CODE = 2002

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
        }

        // Debug code:
        val libDir = applicationInfo.nativeLibraryDir
        Log.i("Check", "nativeLibraryDir = $libDir")
        File(libDir).listFiles()?.forEach { f ->
            Log.i("Check", " - ${f.name} size=${f.length()} exec=${f.canExecute()}")
        }

        /*
        val gradlewSource = File("/storage/emulated/0/Documents/multitouch-cubes-demo/android/build/gradlew")
        val gradlewDest = File(debianRootfs, "/tmp/gradlew")
        Files.copy(gradlewSource.toPath(), gradlewDest.toPath(), StandardCopyOption.REPLACE_EXISTING)
        gradlewDest.setExecutable(true, false)
         */

        // DEBUG!
        val buildEnv = BuildEnvironment(this, debianRootfs.absolutePath)
        val binds = listOf(
            "/storage/emulated/0/Documents/multitouch-cubes-demo/",
        )
        val args = listOf(
            //"/bin/bash",
            "-c",
            //"bash gradlew tasks",
            "sh gradlew tasks",
            //"java",
            //"/tmp/gradlew tasks",
        )
        buildEnv.executeCommand(
            //"/usr/bin/env",
            "/bin/bash",
            args,
            binds,
            "/storage/emulated/0/Documents/multitouch-cubes-demo/android/build",
        )

        //gradlewDest.delete()

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