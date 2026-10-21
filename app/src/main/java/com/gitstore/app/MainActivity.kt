package com.gitstore.app

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ContextCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF6750A4),
                    background = Color(0xFF1C1B1F),
                    surface = Color(0xFF2B2930),
                    onSurface = Color(0xFFE6E1E5)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GitStoreApp()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitStoreApp() {
    var appList by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    // Funzione per scaricare il JSON dal repository GitHub
    fun fetchApps() {
        isLoading = true
        errorMessage = null
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            try {
                // Sostituisci con il link RAW del tuo apps.json su GitHub
                val urlString = "https://raw.githubusercontent.com/kevinkk62/kevinkk62.github.io/main/apps.json"
                val result = URL(urlString).readText()
                val jsonArray = JSONArray(result)
                val tempAppList = mutableListOf<AppItem>()

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    tempAppList.add(
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
                    appList = tempAppList
                    isLoading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorMessage = "Errore di caricamento: ${e.localizedMessage}"
                    isLoading = false
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        fetchApps()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text("GitStore", fontWeight = FontWeight.Bold, fontSize = 22.sp) 
                },
                actions = {
                    IconButton(onClick = { fetchApps() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Aggiorna lista")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary
                )
            } else if (errorMessage != null) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = errorMessage!!, color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { fetchApps() }) {
                        Text("Riprova")
                    }
                }
            } else if (appList.isEmpty()) {
                Text(
                    text = "Nessuna app disponibile nel repository.",
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(appList) { app ->
                        AppCard(app = app) {
                            // Azione tap su Installa/Aggiorna (Scarica l'APK)
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(app.apkUrl))
                            context.startActivity(intent)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppCard(app: AppItem, onInstallClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Versione: ${app.versionName} (v${app.versionCode})",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                Button(
                    onClick = onInstallClick,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Installa", fontSize = 14.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = app.description,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
