package com.gitstore.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class AppItem(
    val packageName: String,
    val name: String,
    val versionCode: Int,
    val versionName: String,
    val description: String,
    val apkUrl: String
)

class MainActivity : ComponentActivity() {

    // Cambia questo URL con quello del TUO file apps.json su GitHub
    private val jsonRepoUrl = "https://raw.githubusercontent.com/TUO_UTENTE/TUO_REPO/main/apps.json"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen(
                        repoUrl = jsonRepoUrl,
                        onInstallClick = { app -> checkPermissionAndDownload(app) }
                    )
                }
            }
        }
    }

    private fun checkPermissionAndDownload(app: AppItem) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!packageManager.canRequestPackageInstalls()) {
                Toast.makeText(this, "Autorizza l'installazione di app da questa fonte", Toast.LENGTH_LONG).show()
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
                return
            }
        }
        downloadAndInstallApk(app)
    }

    private fun downloadAndInstallApk(app: AppItem) {
        Toast.makeText(this, "Download di ${app.name} in corso...", Toast.LENGTH_SHORT).show()
        
        // Esegue il download in background
        Thread {
            try {
                val url = URL(app.apkUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.connect()

                val apkFile = File(cacheDir, "${app.packageName}.apk")
                connection.inputStream.use { input ->
                    apkFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                // Avvia il prompt di installazione nativo
                runOnUiThread {
                    installApk(apkFile)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this, "Errore durante il download: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun installApk(file: File) {
        val uri: Uri = FileProvider.getUriForFile(
            this,
            "$packageName.provider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        startActivity(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(repoUrl: String, onInstallClick: (AppItem) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            try {
                val jsonString = URL(repoUrl).readText()
                val jsonArray = JSONArray(jsonString)
                val list = mutableListOf<AppItem>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    list.add(
                        AppItem(
                            packageName = obj.getString("package_name"),
                            name = obj.getString("name"),
                            versionCode = obj.getInt("version_code"),
                            versionName = obj.getString("version_name"),
                            description = obj.getString("description"),
                            apkUrl = obj.getString("apk_url")
                        )
                    )
                }
                withContext(Dispatchers.Main) {
                    apps = list
                    isLoading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isLoading = false
                }
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("GitStore") }) }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(apps) { app ->
                    AppRow(app = app, context = context, onInstallClick = onInstallClick)
                }
            }
        }
    }
}

@Composable
fun AppRow(app: AppItem, context: Context, onInstallClick: (AppItem) -> Unit) {
    val installedVersionCode = getInstalledVersionCode(context, app.packageName)
    
    val buttonText = when {
        installedVersionCode == -1 -> "Installa"
        app.versionCode > installedVersionCode -> "Aggiorna"
        else -> "Apri"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = app.name, style = MaterialTheme.typography.titleMedium)
                Text(text = "v${app.versionName}", style = MaterialTheme.typography.bodySmall)
                Text(text = app.description, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                if (buttonText == "Apri") {
                    val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                    if (intent != null) context.startActivity(intent)
                } else {
                    onInstallClick(app)
                }
            }) {
                Text(buttonText)
            }
        }
    }
}

fun getInstalledVersionCode(context: Context, packageName: String): Int {
    return try {
        val info = context.packageManager.getPackageInfo(packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            info.versionCode
        }
    } catch (e: PackageManager.NameNotFoundException) {
        -1
    }
}
