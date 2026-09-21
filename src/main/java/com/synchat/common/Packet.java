package com.synchat.common;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * One line on the wire. Serialised to a single-line JSON document.
 *
 * <pre>{ "type":"LOGIN", "id":"c-7", "data":{ "username":"ana", "password":"..." } }</pre>
 */
public class Packet {

    private String type;
    private String id;              // correlation id; null for server pushes
    private JsonObject data = new JsonObject();

    public Packet() {
    }

    private Packet(String type) {
        this.type = type;
    }

    /* ------------------------------------------------------------ factory */

    public static Packet of(String type) {
        return new Packet(type);
    }

    /** Successful RESPONSE. */
    public static Packet ok() {
        return new Packet(Protocol.RESPONSE).put("ok", true);
    }

    /** Failed RESPONSE carrying a human readable reason. */
    public static Packet error(String message) {
        return new Packet(Protocol.RESPONSE).put("ok", false).put("message", message);
    }

    /* ------------------------------------------------------------ writers */

    public Packet put(String key, String value) {
        data.addProperty(key, value);
        return this;
    }

    public Packet put(String key, Number value) {
        data.addProperty(key, value);
        return this;
    }

    public Packet put(String key, boolean value) {
        data.addProperty(key, value);
        return this;
    }

    /** Serialises any POJO / collection into the data object. */
    public Packet putJson(String key, Object value) {
        data.add(key, JsonUtil.GSON.toJsonTree(value));
        return this;
    }

    /* ------------------------------------------------------------ readers */

    public String getString(String key) {
        JsonElement e = data.get(key);
        return (e == null || e.isJsonNull()) ? null : e.getAsString();
    }

    public int getInt(String key) {
        JsonElement e = data.get(key);
        return (e == null || e.isJsonNull()) ? 0 : e.getAsInt();
    }

    public long getLong(String key) {
        JsonElement e = data.get(key);
        return (e == null || e.isJsonNull()) ? 0L : e.getAsLong();
    }

    public boolean getBoolean(String key) {
        JsonElement e = data.get(key);
        return e != null && !e.isJsonNull() && e.getAsBoolean();
    }

    public <T> T getObject(String key, Class<T> type) {
        JsonElement e = data.get(key);
        return (e == null || e.isJsonNull()) ? null : JsonUtil.GSON.fromJson(e, type);
    }

    public <T> List<T> getList(String key, Class<T> type) {
        List<T> out = new ArrayList<>();
        JsonElement e = data.get(key);
        if (e != null && e.isJsonArray()) {
            for (JsonElement item : e.getAsJsonArray()) {
                out.add(JsonUtil.GSON.fromJson(item, type));
            }
        }
        return out;
    }

    public JsonArray getArray(String key) {
        JsonElement e = data.get(key);
        return (e != null && e.isJsonArray()) ? e.getAsJsonArray() : new JsonArray();
    }

    /** Shortcut for the "ok" flag of a RESPONSE packet. */
    public boolean isOk() {
        return getBoolean("ok");
    }

    /** Shortcut for the "message" field of a failed RESPONSE. */
    public String errorMessage() {
        String m = getString("message");
        return m == null ? "Unknown error" : m;
    }

    /* ------------------------------------------------------- accessors */

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getId() {
        return id;
    }

    public Packet setId(String id) {
        this.id = id;
        return this;
    }

    public JsonObject data() {
        return data;
    }

    /* ------------------------------------------------------ (de)serialise */

    public String toJson() {
        return JsonUtil.GSON.toJson(this);
    }

    public static Packet fromJson(String line) {
        Packet p = JsonUtil.GSON.fromJson(line, Packet.class);
        if (p.data == null) {
            p.data = new JsonObject();
        }
        return p;
    }

    @Override
    public String toString() {
        return toJson();
    }
}
