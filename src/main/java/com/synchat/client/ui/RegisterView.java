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

/** Create an account, with an explicit "is this username free?" check. */
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

        setSpacing(12);
        setPadding(new Insets(30));
        setAlignment(Pos.CENTER);

        Label title = new Label("Create your account");
        title.setFont(Font.font("System", FontWeight.BOLD, 22));

        usernameField.setPromptText("Username (3-20 characters)");
        usernameField.setMaxWidth(220);
        passwordField.setPromptText("Password (min 4 characters)");
        passwordField.setMaxWidth(260);
        confirmField.setPromptText("Repeat password");
        confirmField.setMaxWidth(260);

        Button checkButton = new Button("Check");
        checkButton.setOnAction(e -> checkUsername());

        HBox usernameRow = new HBox(8, usernameField, checkButton);
        usernameRow.setAlignment(Pos.CENTER);
        usernameRow.setMaxWidth(300);

        Button registerButton = new Button("Register");
        registerButton.setDefaultButton(true);
        registerButton.setPrefWidth(120);
        registerButton.setOnAction(e -> doRegister());

        Button backButton = new Button("Back to login");
        backButton.setPrefWidth(120);
        backButton.setOnAction(e -> app.showLogin());

        HBox buttons = new HBox(10, registerButton, backButton);
        buttons.setAlignment(Pos.CENTER);

        statusLabel.setWrapText(true);
        statusLabel.setMaxWidth(300);
        statusLabel.setAlignment(Pos.CENTER);

        getChildren().addAll(title, usernameRow, passwordField, confirmField, buttons, statusLabel);
    }

    /** Asks the server whether the username is still free. */
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
