package com.inspiredandroid.kai.tools

import com.inspiredandroid.kai.codeassist.CodeAssistBridgeHttp
import com.inspiredandroid.kai.network.tools.ParameterSchema
import com.inspiredandroid.kai.network.tools.Tool
import com.inspiredandroid.kai.network.tools.ToolInfo
import com.inspiredandroid.kai.network.tools.ToolSchema
import kotlin.time.Duration.Companion.seconds

/**
 * Outils agent Kai / MegaJarvis ai pour le **vrai** pont Jarvis Bridge → CodeAssist.
 * À enregistrer dans [com.inspiredandroid.kai.getAvailableTools] (Platform.android.kt).
 *
 * Les résultats d'outils restent dans la boucle agent (style Kai) — pas de simulation.
 */
object CodeAssistTools {

    const val ID = "codeassist_bridge"

    val toolInfo = ToolInfo(
        id = ID,
        name = "CodeAssist Bridge",
        description = "Lire/écrire/compiler le projet ouvert dans CodeAssist via Jarvis Bridge (127.0.0.1:8080)",
        // nameRes / descriptionRes absents : ToolInfo accepte souvent des défauts — adapter si compile échoue
        userToggleable = true,
    )

    private fun bridge(): CodeAssistBridgeHttp = CodeAssistBridgeHttp.shared

    // —— health ——
    object HealthTool : Tool {
        override val schema = ToolSchema(
            name = "codeassist_health",
            description = """Vérifie le pont Jarvis Bridge ↔ CodeAssist (GET /health).
Retourne connected, root du projet, capabilities.
Si offline : ouvrir CodeAssist, charger le projet, palette « Jarvis Bridge : connecter le projet ».""",
            parameters = emptyMap(),
        )
        override val timeout = 15.seconds
        override suspend fun execute(args: Map<String, Any>): Any = bridge().health()
    }

    // —— list ——
    object ListFilesTool : Tool {
        override val schema = ToolSchema(
            name = "codeassist_list_files",
            description = """Liste les fichiers/dossiers du projet CodeAssist ouvert (GET /files).
path optionnel relatif à la racine du projet (ex. app/src/main/java).""",
            parameters = mapOf(
                "path" to ParameterSchema("string", "Chemin relatif (vide = racine projet)", false),
            ),
        )
        override val timeout = 30.seconds
        override suspend fun execute(args: Map<String, Any>): Any {
            val path = (args["path"] as? String)?.trim().orEmpty()
            return bridge().listFiles(path)
        }
    }

    // —— read ——
    object ReadFileTool : Tool {
        override val schema = ToolSchema(
            name = "codeassist_read_file",
            description = "Lit un fichier du projet CodeAssist (GET /file?path=).",
            parameters = mapOf(
                "path" to ParameterSchema("string", "Chemin relatif, ex. app/src/main/java/.../MainActivity.kt", true),
            ),
        )
        override val timeout = 60.seconds
        override suspend fun execute(args: Map<String, Any>): Any {
            val path = (args["path"] as? String)?.trim()
                ?: return mapOf("success" to false, "error" to "path requis")
            return bridge().readFile(path)
        }
    }

    // —— write ——
    object WriteFileTool : Tool {
        override val schema = ToolSchema(
            name = "codeassist_write_file",
            description = """Écrit/crée un fichier dans le projet CodeAssist (POST /file).
Utilise le contenu complet du fichier. Reste dans le projet actuellement lié au pont.""",
            parameters = mapOf(
                "path" to ParameterSchema("string", "Chemin relatif du fichier", true),
                "content" to ParameterSchema("string", "Contenu complet à écrire", true),
            ),
        )
        override val timeout = 60.seconds
        override suspend fun execute(args: Map<String, Any>): Any {
            val path = (args["path"] as? String)?.trim()
                ?: return mapOf("success" to false, "error" to "path requis")
            val content = args["content"] as? String
                ?: return mapOf("success" to false, "error" to "content requis")
            return bridge().writeFile(path, content)
        }
    }

    // —— delete ——
    object DeleteFileTool : Tool {
        override val schema = ToolSchema(
            name = "codeassist_delete_file",
            description = "Supprime un fichier du projet CodeAssist (DELETE /file). Pas de suppression de dossiers racines.",
            parameters = mapOf(
                "path" to ParameterSchema("string", "Chemin relatif du fichier", true),
            ),
        )
        override val timeout = 30.seconds
        override suspend fun execute(args: Map<String, Any>): Any {
            val path = (args["path"] as? String)?.trim()
                ?: return mapOf("success" to false, "error" to "path requis")
            return bridge().deleteFile(path)
        }
    }

    // —— build ——
    object BuildTool : Tool {
        override val schema = ToolSchema(
            name = "codeassist_build",
            description = """Lance un build via le pont (POST /build).
Remonte succès/erreurs réels — n'invente jamais un BUILD SUCCESS.
Si 400/409 : projet non lié ou archive requise.""",
            parameters = emptyMap(),
        )
        override val timeout = 180.seconds
        override suspend fun execute(args: Map<String, Any>): Any = bridge().build()
    }

    /** Tous les outils à ajouter dans platformExtras Android. */
    fun allTools(): List<Tool> = listOf(
        HealthTool,
        ListFilesTool,
        ReadFileTool,
        WriteFileTool,
        DeleteFileTool,
        BuildTool,
    )
}
