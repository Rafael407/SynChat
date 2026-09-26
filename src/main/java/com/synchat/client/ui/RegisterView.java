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


public class RegisterView extends VBox {

    private final ClientApp app;
    private final ClientConnection connection;

    private final TextField usernameField = new TextField();
    private final PasswordField passwordField = new PasswordField();
    private final PasswordField confirmField = new PasswordField();
    private final Label statusLabel = new Label();

    public RegisterView(ClientApp app, ClientConnection connection) {
        this.app = app;
        this.connection = connection;

        setSpacing(0);
        setAlignment(Pos.CENTER);
        setBackground(Theme.fill(Theme.SURFACE, 0));

        Label title = new Label("Create your account");
        Theme.titleStyle(title, 22);

        usernameField.setPromptText("Username (3-20 characters)");
        usernameField.setMaxWidth(220);
        passwordField.setPromptText("Password (min 4 characters)");
        passwordField.setMaxWidth(260);
        confirmField.setPromptText("Repeat password");
        confirmField.setMaxWidth(260);

        Button checkButton = new Button("Check");
        Theme.secondaryButton(checkButton);
        checkButton.setOnAction(e -> checkUsername());

        HBox usernameRow = new HBox(8, usernameField, checkButton);
        usernameRow.setAlignment(Pos.CENTER);
        usernameRow.setMaxWidth(300);

        Button registerButton = new Button("Register");
        registerButton.setDefaultButton(true);
        registerButton.setPrefWidth(120);
        registerButton.setOnAction(e -> doRegister());
        Theme.primaryButton(registerButton);

        Button backButton = new Button("Back to login");
        backButton.setPrefWidth(130);
        backButton.setOnAction(e -> app.showLogin());
        Theme.secondaryButton(backButton);

        HBox buttons = new HBox(10, registerButton, backButton);
        buttons.setAlignment(Pos.CENTER);

        statusLabel.setWrapText(true);
        statusLabel.setMaxWidth(300);
        statusLabel.setAlignment(Pos.CENTER);
        Theme.mutedStyle(statusLabel);

        VBox card = new VBox(12, title, usernameRow, passwordField, confirmField, buttons, statusLabel);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(32));
        card.setMaxWidth(360);
        Theme.card(card);

        getChildren().add(card);
    }

    private void checkUsername() {
        String username = usernameField.getText().trim();
        if (username.isEmpty()) {
            statusLabel.setText("Type a username first.");
            return;
        }
        connection.send(Packet.of(Protocol.REQ_CHECK_USERNAME).put("username", username),
                response -> statusLabel.setText(response.isOk()
                        ? response.getString("message")
                        : response.errorMessage()));
    }

    private void doRegister() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        String confirm = confirmField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            statusLabel.setText("Username and password are required.");
            return;
        }
        if (!password.equals(confirm)) {
            statusLabel.setText("The two passwords do not match.");
            return;
        }

        statusLabel.setText("Creating account...");
        connection.send(Packet.of(Protocol.REQ_REGISTER)
                .put("username", username)
                .put("password", password), response -> {

            if (response.isOk()) {
                Dialogs.info("Account created", "You can now log in as '" + username + "'.");
                app.showLogin();
            } else {
                statusLabel.setText(response.errorMessage());
            }
        });
    }
}