package my.mmu.Kaixuanrssnewsreader.model;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

public class ApiKeyEntry {

    private final String id;
    private final String label;
    private final String key;
    private final long createdAt;

    public ApiKeyEntry(String id, String label, String key, long createdAt) {
        this.id = id;
        this.label = label;
        this.key = key;
        this.createdAt = createdAt;
    }

    public ApiKeyEntry(String label, String key) {
        this(UUID.randomUUID().toString(), label, key, System.currentTimeMillis());
    }

    public String getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public String getKey() {
        return key;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("label", label);
        json.put("key", key);
        json.put("createdAt", createdAt);
        return json;
    }

    public static ApiKeyEntry fromJson(JSONObject json) throws JSONException {
        return new ApiKeyEntry(
                json.getString("id"),
                json.getString("label"),
                json.getString("key"),
                json.optLong("createdAt", 0)
        );
    }

    public String getMaskedKey() {
        if (key == null || key.isEmpty()) return "";
        if (key.length() <= 8) return "****";
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }
}
