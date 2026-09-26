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

/**
 * A small, reusable palette plus a handful of styling helpers, all built with
 * plain JavaFX Java API (Background, Border, Color, Font, DropShadow). None
 * of this touches a stylesheet or Node.setStyle(...) — every visual here is
 * assembled from ordinary objects, not CSS text.
 */
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

    private Theme() {
    }

    /** Flat, rounded, filled background — no border. */
    public static Background fill(Color color, double radius) {
        return new Background(new BackgroundFill(color, new CornerRadii(radius), Insets.EMPTY));
    }

    /** Rounded 1px border, transparent fill. */
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

    /** Solid accent button with white text and a soft drop shadow. */
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

    /** Outlined button, accent text, transparent fill — for secondary actions. */
    public static void secondaryButton(Button button) {
        button.setBackground(fill(Color.TRANSPARENT, 6));
        button.setBorder(outline(BORDER, 6));
        button.setTextFill(ACCENT_DARK);
        button.setPadding(new Insets(6, 16, 6, 16));
    }

    /** A plain rounded card surface, for grouping content. */
    public static void card(Region region) {
        region.setBackground(fill(SURFACE, 10));
        region.setBorder(outline(BORDER, 10));
    }
}