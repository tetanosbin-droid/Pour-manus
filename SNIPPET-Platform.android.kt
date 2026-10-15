// --- imports ---
import com.inspiredandroid.kai.tools.CodeAssistTools

// --- getPlatformToolDefinitions() : ajouter dans la listOf( ---
        CodeAssistTools.toolInfo,

// --- getAvailableTools() platformExtras : ajouter ---
        // MegaJarvis ai — pont HTTP Jarvis Bridge ↔ CodeAssist (toujours actif)
        addAll(CodeAssistTools.allTools())
