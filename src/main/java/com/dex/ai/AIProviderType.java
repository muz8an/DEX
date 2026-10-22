package com.dex.ai;

/**
 * Enumeration of all supported AI providers in DEX.
 */
public enum AIProviderType {
    GEMINI("Google Gemini", "https://generativelanguage.googleapis.com", "gemini-1.5-flash", true, true, "GEMINI_API_KEY"),
    OPENAI("OpenAI GPT", "https://api.openai.com/v1", "gpt-4o-mini", true, true, "OPENAI_API_KEY"),
    CLAUDE("Anthropic Claude", "https://api.anthropic.com/v1", "claude-3-5-sonnet-20241022", true, true, "ANTHROPIC_API_KEY"),
    GROQ("Groq Llama", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile", false, true, "GROQ_API_KEY"),
    OPENROUTER("OpenRouter", "https://openrouter.ai/api/v1", "auto", true, true, "OPENROUTER_API_KEY"),
    FREELLMAPI("FreeLLMAPI (Local)", "http://127.0.0.1:31415/v1", "auto", false, true, "DEX_API_KEY"),
    OLLAMA("Ollama (Local)", "http://127.0.0.1:11434/v1", "llama3", false, true, ""),
    CUSTOM("Custom OpenAI-Compatible", "http://127.0.0.1:8000/v1", "default", false, true, "CUSTOM_API_KEY");

    private final String displayName;
    private final String defaultBaseUrl;
    private final String defaultModel;
    private final boolean supportsVision;
    private final boolean supportsTools;
    private final String defaultEnvVar;

    AIProviderType(String displayName, String defaultBaseUrl, String defaultModel, boolean supportsVision, boolean supportsTools, String defaultEnvVar) {
        this.displayName = displayName;
        this.defaultBaseUrl = defaultBaseUrl;
        this.defaultModel = defaultModel;
        this.supportsVision = supportsVision;
        this.supportsTools = supportsTools;
        this.defaultEnvVar = defaultEnvVar;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDefaultBaseUrl() {
        return defaultBaseUrl;
    }

    public String getDefaultModel() {
        return defaultModel;
    }

    public boolean isSupportsVision() {
        return supportsVision;
    }

    public boolean isSupportsTools() {
        return supportsTools;
    }

    public String getDefaultEnvVar() {
        return defaultEnvVar;
    }

    public static AIProviderType fromString(String text) {
        if (text == null || text.isBlank()) return FREELLMAPI;
        String normalized = text.trim().toUpperCase().replace("-", "_").replace(" ", "_");
        for (AIProviderType t : values()) {
            if (t.name().equalsIgnoreCase(normalized) || t.getDisplayName().equalsIgnoreCase(text.trim())) {
                return t;
            }
        }
        return CUSTOM;
    }
}
