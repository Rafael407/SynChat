package com.synchat.client.Joke_api;

import com.synchat.common.JsonUtil;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

//fetching joke_api jokes as http request
public final class JokeApiClient {

    private static final String JOKE_URL =
            "https://v2.jokeapi.dev/joke/Any?type=single&safe-mode";

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private JokeApiClient() {
    }

    public static Joke fetchRandomJoke() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(JOKE_URL))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Unexpected HTTP status: " + response.statusCode());
        }

        Joke joke = JsonUtil.GSON.fromJson(response.body(), Joke.class);
        if (joke == null || joke.isError() || joke.getJoke() == null) {
            throw new IOException("JokeAPI returned no usable joke");
        }
        return joke;
    }
}