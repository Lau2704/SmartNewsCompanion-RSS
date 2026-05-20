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
            "gemini-2.5-flash",
            "https://aistudio.google.com/apikey",
            R.string.gemini_api_key_instruction,
            R.string.gemini_model_examples,
            false),

    OPENAI("openai",
            R.string.provider_openai,
            "https://api.openai.com/v1/chat/completions",
            "gpt-4.1-mini",
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

    XAI_GROK("xai_grok",
            R.string.provider_xai_grok,
            "https://api.x.ai/v1/chat/completions",
            "grok-4.3",
            "https://console.x.ai/team/default/api-keys",
            R.string.xai_grok_api_key_instruction,
            R.string.xai_grok_model_examples,
            false),

    DEEPSEEK("deepseek",
            R.string.provider_deepseek,
            "https://api.deepseek.com/chat/completions",
            "deepseek-v4-flash",
            "https://platform.deepseek.com/api_keys",
            R.string.deepseek_api_key_instruction,
            R.string.deepseek_model_examples,
            false),

    QWEN("qwen",
            R.string.provider_qwen,
            "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
            "qwen3.6-plus",
            "https://dashscope.console.aliyun.com/",
            R.string.qwen_api_key_instruction,
            R.string.qwen_model_examples,
            false),

    MINIMAX_CN("minimax_cn",
            R.string.provider_minimax_cn,
            "https://api.minimaxi.com/v1/chat/completions",
            "MiniMax-M2.7",
            "https://platform.minimaxi.com/user-center/basic-information/interface-key",
            R.string.minimax_cn_api_key_instruction,
            R.string.minimax_cn_model_examples,
            false),

    MINIMAX_GLOBAL("minimax_global",
            R.string.provider_minimax_global,
            "https://api.minimax.io/v1/chat/completions",
            "MiniMax-M2.7",
            "https://platform.minimax.io/user-center/basic-information",
            R.string.minimax_global_api_key_instruction,
            R.string.minimax_global_model_examples,
            false),

    GLM_CN("glm_cn",
            R.string.provider_glm_cn,
            "https://open.bigmodel.cn/api/paas/v4/chat/completions",
            "glm-5.1",
            "https://bigmodel.cn/console/",
            R.string.glm_cn_api_key_instruction,
            R.string.glm_cn_model_examples,
            false),

    GLM_GLOBAL("glm_global",
            R.string.provider_glm_global,
            "https://api.z.ai/chat/completions",
            "glm-5.1",
            "https://z.ai/manage-apikey/apikey-list",
            R.string.glm_global_api_key_instruction,
            R.string.glm_global_model_examples,
            false),

    KIMI("kimi",
            R.string.provider_kimi,
            "https://api.moonshot.cn/v1/chat/completions",
            "kimi-k2.6",
            "https://platform.moonshot.cn/",
            R.string.kimi_api_key_instruction,
            R.string.kimi_model_examples,
            false),

    MIMO("mimo",
            R.string.provider_mimo,
            "https://api.xiaomimimo.com/v1/chat/completions",
            "mimo-v2.5-pro",
            "https://xiaomimimo.com/",
            R.string.mimo_api_key_instruction,
            R.string.mimo_model_examples,
            false),

    LOCAL("local",
            R.string.provider_local,
            "",
            "gemma-4-E2B-it",
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
