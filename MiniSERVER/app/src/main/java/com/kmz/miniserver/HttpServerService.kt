package com.kmz.miniserver

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HttpServerService : Service() {

    companion object {
        var isRunning = false
    }

    private var server:
            EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? =
            null
    private val CHANNEL_ID = "HttpServerChannel"
    private val NOTIFICATION_ID = 1

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == "STOP") {
            stopServer()
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, createNotification("Serveur en cours d'exécution..."))
        startServer()
        return START_STICKY
    }

    private fun startServer() {
        if (server != null) return

        isRunning = true
        ServerWidgetProvider.updateAllWidgets(this, true)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                server =
                        embeddedServer(Netty, port = AppConstants.PORT, host = "0.0.0.0") {
                                    install(CORS) {
                                        anyHost()
                                        allowHeader(HttpHeaders.ContentType)
                                        allowMethod(HttpMethod.Options)
                                        allowMethod(HttpMethod.Put)
                                        allowMethod(HttpMethod.Post)
                                        allowMethod(HttpMethod.Get)
                                        allowMethod(HttpMethod.Delete)
                                    }
                                    install(ContentNegotiation) { json() }
                                    routing {
                                        val baseDir = AppConstants.BASE_DIR
                                        if (!baseDir.exists()) baseDir.mkdirs()

                                        get("/") {
                                            val files = baseDir.listFiles()?.toList() ?: emptyList()
                                            call.respondText(
                                                    generateHtmlListing("", files),
                                                    ContentType.Text.Html
                                            )
                                        }

                                        // Servir les fichiers JSON
                                        get("/{path...}") {
                                            val pathSegments = call.parameters.getAll("path")
                                            val relativePath =
                                                    pathSegments?.joinToString(File.separator) ?: ""
                                            val file = File(baseDir, relativePath)

                                            if (file.exists() && file.isFile) {
                                                call.respondFile(file)
                                            } else if (file.exists() && file.isDirectory) {
                                                val files =
                                                        file.listFiles()?.toList() ?: emptyList()
                                                call.respondText(
                                                        generateHtmlListing(relativePath, files),
                                                        ContentType.Text.Html
                                                )
                                            } else {
                                                call.respond(
                                                        HttpStatusCode.NotFound,
                                                        "File not found: $relativePath"
                                                )
                                            }
                                        }

                                        // Upload/Update fichiers JSON
                                        post("/{path...}") { handleFileUpload(call, baseDir) }
                                        put("/{path...}") { handleFileUpload(call, baseDir) }
                                    }
                                }
                                .start(wait = false)

                val ip = NetworkUtils.getLocalIpAddress() ?: "127.0.0.1"
                updateNotification("Serveur actif sur http://$ip:${AppConstants.PORT}")
            } catch (e: Exception) {
                Log.e("MiniServer", "Erreur au démarrage du serveur", e)
                isRunning = false
                ServerWidgetProvider.updateAllWidgets(this@HttpServerService, false)
                updateNotification("ERREUR: Impossible de démarrer le serveur")
            }
        }
    }

    private fun updateNotification(text: String) {
        val notification = createNotification(text)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private suspend fun handleFileUpload(call: ApplicationCall, baseDir: File) {
        val pathSegments = call.parameters.getAll("path")
        val relativePath = pathSegments?.joinToString(File.separator) ?: "new_file.json"
        val file = File(baseDir, relativePath)

        // Créer les dossiers parents si nécessaire
        file.parentFile?.mkdirs()

        try {
            val bytes = call.receive<ByteArray>()
            file.writeBytes(bytes)
            call.respond(HttpStatusCode.OK, "File saved: $relativePath")
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, "Error saving file: ${e.message}")
        }
    }

    private fun stopServer() {
        server?.stop(1000, 2000)
        server = null
        isRunning = false
        ServerWidgetProvider.updateAllWidgets(this, false)
    }

    override fun onDestroy() {
        stopServer()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel =
                    NotificationChannel(
                            CHANNEL_ID,
                            "Mini Server Service Channel",
                            NotificationManager.IMPORTANCE_LOW
                    )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(contentText: String): Notification {
        val stopIntent = Intent(this, HttpServerService::class.java).apply { action = "STOP" }
        val stopPendingIntent =
                PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        val mainIntent = Intent(this, MainActivity::class.java)
        val mainPendingIntent =
                PendingIntent.getActivity(this, 0, mainIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Mini SERVER")
                .setContentText(contentText)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(mainPendingIntent)
                .addAction(
                        android.R.drawable.ic_menu_close_clear_cancel,
                        "ARRÊTER",
                        stopPendingIntent
                )
                .build()
    }

    private fun generateHtmlListing(path: String, files: List<File>): String {
        val title = if (path.isEmpty()) "/" else "/$path"
        val sb = StringBuilder()
        sb.append("<!DOCTYPE html><html><head>")
        sb.append("<meta charset=\"UTF-8\">")
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">")
        sb.append("<title>Mini SERVER - $title</title>")
        sb.append("<style>")
        sb.append(
                "body { font-family: sans-serif; background: #121212; color: #eee; padding: 20px; }"
        )
        sb.append("h1 { color: #00E676; }")
        sb.append("ul { list-style: none; padding: 0; }")
        sb.append(
                "li { padding: 10px; border-bottom: 1px solid #333; display: flex; align-items: center; }"
        )
        sb.append("a { color: #2196F3; text-decoration: none; flex-grow: 1; font-weight: bold; }")
        sb.append("a:hover { text-decoration: underline; }")
        sb.append(".dir { color: #FFA726; }")
        sb.append(".size { color: #888; font-size: 0.9em; margin-left: 10px; }")
        sb.append(".back { margin-bottom: 20px; display: inline-block; color: #888; }")
        sb.append("</style></head><body>")
        sb.append("<h1>Mini SERVER</h1>")
        sb.append("<h3>Explorateur: $title</h3>")

        if (path.isNotEmpty()) {
            sb.append("<a href=\"..\" class=\"back\">⬅ Retour</a>")
        }

        sb.append("<ul>")
        // Sort: directories first, then alphabetical
        val sortedFiles = files.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))

        for (file in sortedFiles) {
            val name = file.name
            val isDir = file.isDirectory
            val displayName = if (isDir) "$name/" else name
            val cssClass = if (isDir) "dir" else ""
            val href = if (path.isEmpty()) name else "$name" // Relative to current

            sb.append("<li>")
            sb.append("<a href=\"$href\" class=\"$cssClass\">$displayName</a>")
            if (!isDir) {
                val size = file.length() / 1024
                sb.append("<span class=\"size\">$size KB</span>")
            }
            sb.append("</li>")
        }
        sb.append("</ul>")
        sb.append("</body></html>")
        return sb.toString()
    }
}
