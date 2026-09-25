package com.synchat.client;

import com.synchat.client.net.ClientConnection;
import com.synchat.client.ui.LoginView;
import com.synchat.client.ui.MainView;
import com.synchat.client.ui.RegisterView;
import com.synchat.common.Protocol;
import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * JavaFX entry point. Owns the single {@link ClientConnection} and swaps the
 * root node of the scene when moving between login / register / chat.
 *
 *   mvn javafx:run
 */
public class ClientApp extends Application {

    private final ClientConnection connection = new ClientConnection();
    private Stage stage;
    private Scene scene;

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        stage.setTitle("SynChat");

        scene = new Scene(new LoginView(this, connection), 420, 320);
        stage.setScene(scene);
        stage.show();

        connect();
    }

    /** Opens the TCP connection to the local server. */
    private void connect() {
        String host = System.getProperty("server.host",Protocol.HOST);
        try {
            connection.connect(host, Protocol.PORT);
            connection.setOnDisconnect(this::handleDisconnect);
        } catch (IOException e) {
            Alert alert = new Alert(Alert.AlertType.ERROR,
                    "Could not reach the SynChat server at "
                            + host + ":" + Protocol.PORT + ".\n\n"
                            + "Start it first with:  mvn exec:java\n\n"
                            + "Details: " + e.getMessage());
            alert.setHeaderText("Server unavailable");
            alert.showAndWait();
        }
    }

    private void handleDisconnect() {
        connection.clearAllListeners();
        Alert alert = new Alert(Alert.AlertType.WARNING, "The connection to the server was lost.");
        alert.setHeaderText("Disconnected");
        alert.show();
        setRoot(new LoginView(this, connection), 420, 320, "SynChat");
    }

    /* ------------------------------------------------------- navigation */

    public void showLogin() {
        connection.clearAllListeners();
        setRoot(new LoginView(this, connection), 420, 320, "SynChat");
    }

    public void showRegister() {
        setRoot(new RegisterView(this, connection), 420, 380, "SynChat - Create account");
    }

    public void showMain(String username) {
        setRoot(new MainView(this, connection, username), 900, 580, "SynChat - " + username);
    }

    private void setRoot(Parent root, double width, double height, String title) {
        scene.setRoot(root);
        stage.setTitle(title);
        stage.setWidth(width);
        stage.setHeight(height);
        stage.centerOnScreen();
    }

    @Override
    public void stop() {
        connection.close();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
