package my.mmu.Kaixuanrssnewsreader.model;

import my.mmu.Kaixuanrssnewsreader.R;

public enum ApiProvider {

    GROQ("groq",
            R.string.provider_groq,
            "https://api.groq.com/openai/v1/chat/completions",
            "llama-3.3-70b-versatile",
            "https://console.groq.com/keys",
            R.string.groq_api_key_instruction,
            R.string.groq_model_examples,
            false),

    OPENROUTER("openrouter",
            R.string.provider_openrouter,
            "https://openrouter.ai/api/v1/chat/completions",
            "openai/gpt-oss-20b:free",
            "https://openrouter.ai/settings/keys",
            R.string.openrouter_api_key_instruction,
            R.string.openrouter_model_examples,
            false),

    GEMINI("gemini",
            R.string.provider_gemini,
            "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
            "gemini-2.0-flash",
            "https://aistudio.google.com/apikey",
            R.string.gemini_api_key_instruction,
            R.string.gemini_model_examples,
            false),

    OPENAI("openai",
            R.string.provider_openai,
            "https://api.openai.com/v1/chat/completions",
            "gpt-4o-mini",
            "https://platform.openai.com/api-keys",
            R.string.openai_api_key_instruction,
            R.string.openai_model_examples,
            false),

    CLAUDE("claude",
            R.string.provider_claude,
            "https://api.anthropic.com/v1/messages",
            "claude-sonnet-4-20250514",
            "https://console.anthropic.com/settings/keys",
            R.string.claude_api_key_instruction,
            R.string.claude_model_examples,
            true),

    LOCAL("local",
            R.string.provider_local,
            "",
            "gemma-2-2b-it-Q4_K_M",
            "",
            0,
            0,
            false);

    private final String key;
    private final int displayNameRes;
    private final String endpoint;
    private final String defaultModel;
    private final String consoleUrl;
    private final int instructionRes;
    private final int modelExamplesRes;
    private final boolean anthropicFormat;

    ApiProvider(String key, int displayNameRes, String endpoint, String defaultModel,
                String consoleUrl, int instructionRes, int modelExamplesRes, boolean anthropicFormat) {
        this.key = key;
        this.displayNameRes = displayNameRes;
        this.endpoint = endpoint;
        this.defaultModel = defaultModel;
        this.consoleUrl = consoleUrl;
        this.instructionRes = instructionRes;
        this.modelExamplesRes = modelExamplesRes;
        this.anthropicFormat = anthropicFormat;
    }

    public String getKey() {
        return key;
    }

    public int getDisplayNameRes() {
        return displayNameRes;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getDefaultModel() {
        return defaultModel;
    }

    public String getConsoleUrl() {
        return consoleUrl;
    }

    public int getInstructionRes() {
        return instructionRes;
    }

    public int getModelExamplesRes() {
        return modelExamplesRes;
    }

    public boolean isAnthropicFormat() {
        return anthropicFormat;
    }

    public boolean isLocal() {
        return this == LOCAL;
    }

    public String getApiKeyPreferenceKey() {
        return key + "ApiKey";
    }

    public String getModelPreferenceKey() {
        return key + "Model";
    }

    public static ApiProvider fromKey(String key) {
        if (key == null || key.isEmpty()) {
            return GROQ;
        }
        for (ApiProvider provider : values()) {
            if (provider.key.equals(key)) {
                return provider;
            }
        }
        return GROQ;
    }
}
