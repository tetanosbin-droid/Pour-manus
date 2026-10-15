package com.inspiredandroid.kai.codeassist

import com.inspiredandroid.kai.httpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Client HTTP du plugin **Jarvis Bridge** dans CodeAssist.
 *
 * Routes réelles (plugin FS) :
 * - GET  /health
 * - GET  /files?path=
 * - GET  /file?path=
 * - POST /file  { "path", "content" }
 * - DELETE /file?path=
 * - POST /build (body = archive .caproj optionnelle)
 *
 * Prérequis côté téléphone :
 * 1. CodeAssist ouvert + projet chargé
 * 2. Palette → « Jarvis Bridge : connecter le projet » (une fois)
 * 3. Serveur local 127.0.0.1:8080
 *
 * Ce client ne simule rien : en cas d'échec réseau, l'erreur est renvoyée telle quelle.
 */
class CodeAssistBridgeHttp(
    var host: String = "127.0.0.1",
    var port: Int = 8080,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client = httpClient {
        install(HttpTimeout) {
            connectTimeoutMillis = 5_000
            requestTimeoutMillis = 180_000
            socketTimeoutMillis = 180_000
        }
    }

    private fun base() = "http://$host:$port"

    suspend fun health(): Map<String, Any?> {
        return try {
            val response = client.get("${base()}/health")
            val text = response.bodyAsText()
            if (!response.status.isSuccess()) {
                return mapOf(
                    "success" to false,
                    "error" to "HTTP ${response.status.value}: $text",
                    "hint" to "Lance CodeAssist + plugin Jarvis Bridge, puis palette « connecter le projet ».",
                )
            }
            val obj = json.parseToJsonElement(text).jsonObject
            val connected = obj["connected"]?.jsonPrimitive?.booleanOrNull == true
            mapOf(
                "success" to true,
                "connected" to connected,
                "serverRunning" to (obj["serverRunning"]?.jsonPrimitive?.booleanOrNull ?: true),
                "protocolVersion" to (obj["protocolVersion"]?.jsonPrimitive?.intOrNull ?: 0),
                "root" to (obj["root"]?.jsonPrimitive?.contentOrNull),
                "hint" to (obj["hint"]?.jsonPrimitive?.contentOrNull),
                "capabilities" to (obj["capabilities"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList<String>()),
                "raw" to text.take(1500),
            )
        } catch (e: Exception) {
            mapOf(
                "success" to false,
                "error" to (e.message ?: e.toString()),
                "hint" to "Impossible de joindre $host:$port. CodeAssist + Jarvis Bridge doivent tourner sur le même appareil.",
            )
        }
    }

    suspend fun listFiles(path: String = ""): Map<String, Any?> {
        return try {
            val response = client.get("${base()}/files") {
                if (path.isNotBlank()) parameter("path", path)
            }
            val text = response.bodyAsText()
            if (!response.status.isSuccess()) {
                return mapOf("success" to false, "error" to "HTTP ${response.status.value}: $text")
            }
            val obj = json.parseToJsonElement(text).jsonObject
            val arr = obj["files"]?.jsonArray
                ?: obj["entries"]?.jsonArray
                ?: obj["items"]?.jsonArray
                ?: JsonArray(emptyList())
            val items = arr.mapNotNull { el ->
                val o = el.jsonObject
                mapOf(
                    "path" to (o["path"]?.jsonPrimitive?.contentOrNull ?: o["name"]?.jsonPrimitive?.contentOrNull ?: ""),
                    "name" to (o["name"]?.jsonPrimitive?.contentOrNull ?: ""),
                    "isDirectory" to (o["isDirectory"]?.jsonPrimitive?.booleanOrNull
                        ?: o["dir"]?.jsonPrimitive?.booleanOrNull
                        ?: false),
                    "size" to (o["size"]?.jsonPrimitive?.longOrNull ?: o["sizeBytes"]?.jsonPrimitive?.longOrNull ?: 0L),
                )
            }
            mapOf("success" to true, "path" to path, "count" to items.size, "files" to items)
        } catch (e: Exception) {
            mapOf("success" to false, "error" to (e.message ?: e.toString()))
        }
    }

    suspend fun readFile(path: String): Map<String, Any?> {
        if (path.isBlank()) return mapOf("success" to false, "error" to "path requis")
        return try {
            val response = client.get("${base()}/file") {
                parameter("path", path)
            }
            val text = response.bodyAsText()
            if (!response.status.isSuccess()) {
                return mapOf("success" to false, "error" to "HTTP ${response.status.value}: $text")
            }
            val content = try {
                val o = json.parseToJsonElement(text).jsonObject
                o["content"]?.jsonPrimitive?.contentOrNull ?: text
            } catch (_: Exception) {
                text
            }
            mapOf(
                "success" to true,
                "path" to path,
                "length" to content.length,
                "content" to content.take(200_000),
            )
        } catch (e: Exception) {
            mapOf("success" to false, "error" to (e.message ?: e.toString()))
        }
    }

    suspend fun writeFile(path: String, content: String): Map<String, Any?> {
        if (path.isBlank()) return mapOf("success" to false, "error" to "path requis")
        return try {
            val body = buildJsonObject {
                put("path", path)
                put("content", content)
            }
            val response = client.post("${base()}/file") {
                contentType(ContentType.Application.Json)
                setBody(body.toString())
            }
            val text = response.bodyAsText()
            if (!response.status.isSuccess()) {
                return mapOf("success" to false, "error" to "HTTP ${response.status.value}: $text")
            }
            mapOf("success" to true, "path" to path, "response" to text.take(1000))
        } catch (e: Exception) {
            mapOf("success" to false, "error" to (e.message ?: e.toString()))
        }
    }

    suspend fun deleteFile(path: String): Map<String, Any?> {
        if (path.isBlank()) return mapOf("success" to false, "error" to "path requis")
        return try {
            val response = client.delete("${base()}/file") {
                parameter("path", path)
            }
            val text = response.bodyAsText()
            if (!response.status.isSuccess()) {
                return mapOf("success" to false, "error" to "HTTP ${response.status.value}: $text")
            }
            mapOf("success" to true, "path" to path, "response" to text.take(500))
        } catch (e: Exception) {
            mapOf("success" to false, "error" to (e.message ?: e.toString()))
        }
    }

    /**
     * Build via le pont. Sans archive .caproj, le plugin peut renvoyer 400 —
     * l'erreur réelle est remontée (pas de faux BUILD SUCCESS).
     */
    suspend fun build(optionalCaprojBytes: ByteArray? = null): Map<String, Any?> {
        return try {
            val response = client.post("${base()}/build") {
                if (optionalCaprojBytes != null && optionalCaprojBytes.isNotEmpty()) {
                    contentType(ContentType.Application.OctetStream)
                    setBody(optionalCaprojBytes)
                } else {
                    contentType(ContentType.Application.OctetStream)
                    setBody(ByteArray(0))
                }
            }
            val text = response.bodyAsText()
            if (!response.status.isSuccess()) {
                return mapOf(
                    "success" to false,
                    "httpStatus" to response.status.value,
                    "error" to text.take(4000),
                    "hint" to "Projet lié ? Palette « Jarvis Bridge : connecter le projet ». Certaines versions attendent une archive .caproj.",
                )
            }
            val obj = try {
                json.parseToJsonElement(text).jsonObject
            } catch (_: Exception) {
                null
            }
            val ok = obj?.get("success")?.jsonPrimitive?.booleanOrNull
                ?: (obj?.get("status")?.jsonPrimitive?.contentOrNull == "ok")
            mapOf(
                "success" to ok,
                "body" to text.take(8000),
                "apkPath" to obj?.get("apkPath")?.jsonPrimitive?.contentOrNull,
            )
        } catch (e: Exception) {
            mapOf("success" to false, "error" to (e.message ?: e.toString()))
        }
    }

    companion object {
        @Volatile
        var shared: CodeAssistBridgeHttp = CodeAssistBridgeHttp()
    }
}
