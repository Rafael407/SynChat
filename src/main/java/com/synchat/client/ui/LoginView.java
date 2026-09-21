package com.synchat.client.ui;

import com.synchat.client.ClientApp;
import com.synchat.client.net.ClientConnection;
import com.synchat.common.Packet;
import com.synchat.common.Protocol;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/** Username + password, or a jump to the registration screen. */
public class LoginView extends VBox {

    private final ClientApp app;
    private final ClientConnection connection;

    private final TextField usernameField = new TextField();
    private final PasswordField passwordField = new PasswordField();
    private final Label statusLabel = new Label();
    private final Button loginButton = new Button("Log in");

    public LoginView(ClientApp app, ClientConnection connection) {
        this.app = app;
        this.connection = connection;

        setSpacing(12);
        setPadding(new Insets(30));
        setAlignment(Pos.CENTER);

        Label title = new Label("SynChat");
        title.setFont(Font.font("System", FontWeight.BOLD, 26));

        Label subtitle = new Label("Sign in to continue");

        usernameField.setPromptText("Username");
        usernameField.setMaxWidth(260);
        passwordField.setPromptText("Password");
        passwordField.setMaxWidth(260);

        loginButton.setDefaultButton(true);
        loginButton.setPrefWidth(120);
        loginButton.setOnAction(e -> doLogin());

        Button registerButton = new Button("Create account");
        registerButton.setPrefWidth(130);
        registerButton.setOnAction(e -> app.showRegister());

        HBox buttons = new HBox(10, loginButton, registerButton);
        buttons.setAlignment(Pos.CENTER);

        statusLabel.setWrapText(true);
        statusLabel.setMaxWidth(280);
        statusLabel.setAlignment(Pos.CENTER);

        passwordField.setOnAction(e -> doLogin());

        getChildren().addAll(title, subtitle, usernameField, passwordField, buttons, statusLabel);
    }

    private void doLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            statusLabel.setText("Enter both a username and a password.");
            return;
        }
        if (!connection.isConnected()) {
            statusLabel.setText("Not connected. Start the server, then restart this client.");
            return;
        }

        loginButton.setDisable(true);
        statusLabel.setText("Signing in...");

        connection.send(Packet.of(Protocol.REQ_LOGIN)
                .put("username", username)
                .put("password", password), response -> {

            loginButton.setDisable(false);
            if (response.isOk()) {
                app.showMain(response.getString("username"));
            } else {
                passwordField.clear();
                statusLabel.setText(response.errorMessage());
            }
        });
    }
}
