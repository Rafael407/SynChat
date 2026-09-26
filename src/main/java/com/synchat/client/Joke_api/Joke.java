package com.synchat.client.Joke_api;

/*
 Mirrors a "single" type response from https://v2.jokeapi.dev/joke/Any :
    { "error": false, "category": "Any", "type": "single",  "joke": "...", "id": 123, "safe": true, "lang": "en" }
 */
public class Joke {

    private boolean error;
    private String category;
    private String joke;
    private boolean safe;

    public boolean isError() {
        return error;
    }

    public String getCategory() {
        return category;
    }

    public String getJoke() {
        return joke;
    }

    public boolean isSafe() {
        return safe;
    }
}