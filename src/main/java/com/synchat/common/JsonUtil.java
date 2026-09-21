package com.synchat.common;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/** Single shared Gson instance (Gson is thread safe). */
public final class JsonUtil {

    public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private JsonUtil() {
    }
}
