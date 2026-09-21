package com.synchat.client.ui;

import com.synchat.client.net.ClientConnection;
import com.synchat.common.Packet;
import com.synchat.common.Protocol;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.GridPane;

/** Tiny wrappers so views do not repeat Alert boilerplate. */
public final class Dialogs {

    private Dialogs() {
    }

    public static void info(String header, String message) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, message);
        a.setHeaderText(header);
        a.showAndWait();
    }

    public static void error(String header, String message) {
        Alert a = new Alert(Alert.AlertType.ERROR, message);
        a.setHeaderText(header);
        a.showAndWait();
    }

    /** Old / new / confirm password, then fires CHANGE_PASSWORD. */
    public static void changePassword(ClientConnection connection) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Change password");
        dialog.setHeaderText("Enter your current password and a new one");

        ButtonType okType = new ButtonType("Change", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okType, ButtonType.CANCEL);

        PasswordField oldField = new PasswordField();
        PasswordField newField = new PasswordField();
        PasswordField confirmField = new PasswordField();

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(15));
        grid.addRow(0, new Label("Current password"), oldField);
        grid.addRow(1, new Label("New password"), newField);
        grid.addRow(2, new Label("Repeat new password"), confirmField);
        dialog.getDialogPane().setContent(grid);

        dialog.showAndWait().ifPresent(result -> {
            if (result != okType) {
                return;
            }
            if (!newField.getText().equals(confirmField.getText())) {
                error("Change password", "The two new passwords do not match.");
                return;
            }
            connection.send(Packet.of(Protocol.REQ_CHANGE_PASSWORD)
                    .put("oldPassword", oldField.getText())
                    .put("newPassword", newField.getText()), response -> {

                if (response.isOk()) {
                    info("Change password", response.getString("message"));
                } else {
                    error("Change password", response.errorMessage());
                }
            });
        });
    }
}
