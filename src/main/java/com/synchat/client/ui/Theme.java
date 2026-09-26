package com.synchat.client.ui;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

public final class Theme {

    public static final Color ACCENT = Color.web("#3B5BDB");        // primary brand blue
    public static final Color ACCENT_DARK = Color.web("#2B45B0");
    public static final Color ACCENT_LIGHT = Color.web("#EEF1FD");
    public static final Color SURFACE = Color.web("#FAFAFC");
    public static final Color BORDER = Color.web("#E1E3EC");
    public static final Color TEXT_MUTED = Color.web("#6B6F80");
    public static final Color ONLINE = Color.web("#2F9E44");
    public static final Color OFFLINE = Color.web("#B0B3BE");
    public static final Color BUBBLE_MINE = Color.web("#3B5BDB");
    public static final Color BUBBLE_THEIRS = Color.web("#E9E9EF");
    public static final Color DANGER = Color.web("#E03131");

    private Theme() {
    }

    public static Background fill(Color color, double radius) {
        return new Background(new BackgroundFill(color, new CornerRadii(radius), Insets.EMPTY));
    }

    public static Border outline(Color color, double radius) {
        return new Border(new BorderStroke(color, BorderStrokeStyle.SOLID,
                new CornerRadii(radius), new BorderWidths(1)));
    }

    public static void titleStyle(Label label, double size) {
        label.setFont(Font.font("System", FontWeight.BOLD, size));
        label.setTextFill(ACCENT_DARK);
    }

    public static void mutedStyle(Label label) {
        label.setTextFill(TEXT_MUTED);
    }

    public static void primaryButton(Button button) {
        button.setBackground(fill(ACCENT, 6));
        button.setTextFill(Color.WHITE);
        button.setPadding(new Insets(6, 16, 6, 16));
        DropShadow shadow = new DropShadow();
        shadow.setRadius(6);
        shadow.setOffsetY(2);
        shadow.setColor(Color.web("#000000", 0.15));
        button.setEffect(shadow);
    }

    public static void secondaryButton(Button button) {
        button.setBackground(fill(Color.TRANSPARENT, 6));
        button.setBorder(outline(BORDER, 6));
        button.setTextFill(ACCENT_DARK);
        button.setPadding(new Insets(6, 16, 6, 16));
    }

    public static void dangerButton(Button button) {
        button.setBackground(fill(DANGER, 6));
        button.setTextFill(Color.WHITE);
        button.setPadding(new Insets(6, 16, 6, 16));
    }

    public static void card(Region region) {
        region.setBackground(fill(SURFACE, 10));
        region.setBorder(outline(BORDER, 10));
    }
}