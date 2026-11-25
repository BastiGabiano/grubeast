package com.grubeast;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.stage.FileChooser;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Small helpers for building the screens, so the views stay short. */
public final class Ui {
    private Ui() {}

    // set by App at startup
    static String bodyFont = "Segoe UI";
    static String displayFont = "Impact";

    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);
    public static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.ENGLISH);

    // ---------- text ----------

    public static Label text(String s, String... styleClasses) {
        Label l = new Label(s);
        l.getStyleClass().addAll(styleClasses);
        return l;
    }

    /** A label that wraps onto several lines. */
    public static Label wrap(String s, String... styleClasses) {
        Label l = text(s, styleClasses);
        l.setWrapText(true);
        l.setMinHeight(Region.USE_PREF_SIZE); // stops wrapped text from being cut off with "..."
        return l;
    }

    /** Label in the condensed Impact-style font used for the logo and big titles. */
    public static Label display(String s, String... styleClasses) {
        Label l = text(s, styleClasses);
        l.setStyle("-fx-font-family: '" + displayFont + "';");
        return l;
    }

    public static Label flash(String message, boolean ok) {
        Label l = wrap(message, ok ? "flash-ok" : "flash-err");
        l.setMaxWidth(Double.MAX_VALUE);
        return l;
    }

    public static String initial(String name) {
        return (name == null || name.isBlank()) ? "?" : name.trim().substring(0, 1).toUpperCase();
    }

    public static String peso(BigDecimal price) {
        BigDecimal v = price == null ? BigDecimal.ZERO : price;
        return "₱" + (v.stripTrailingZeros().scale() > 0 ? String.format("%,.2f", v) : String.format("%,.0f", v));
    }

    public static String stars(double average) {
        int r = (int) Math.round(average);
        return "★".repeat(Math.max(0, r)) + "☆".repeat(Math.max(0, 5 - r));
    }

    // ---------- buttons and clicks ----------

    /** Shows an error box instead of crashing silently when the database can't be reached. */
    private static Runnable guard(Runnable action) {
        return () -> {
            try {
                action.run();
            } catch (Db.DbException ex) {
                error(ex.getMessage());
            }
        };
    }

    public static Button button(String text, Runnable action) {
        Button b = new Button(text);
        b.getStyleClass().add("btn");
        Runnable safe = guard(action);
        b.setOnAction(e -> safe.run());
        return b;
    }

    public static Button outline(String text, Runnable action) {
        Button b = new Button(text);
        b.getStyleClass().add("btn-outline");
        Runnable safe = guard(action);
        b.setOnAction(e -> safe.run());
        return b;
    }

    public static Button small(Button b) {
        b.getStyleClass().add("btn-sm");
        return b;
    }

    public static Button link(String text, Runnable action) {
        Button b = new Button(text);
        b.getStyleClass().add("link-btn");
        Runnable safe = guard(action);
        b.setOnAction(e -> safe.run());
        return b;
    }

    public static <T extends Node> T clickable(T node, Runnable action) {
        node.setStyle(node.getStyle() == null ? "-fx-cursor: hand;" : node.getStyle() + "-fx-cursor: hand;");
        node.setOnMouseClicked(e -> action.run());
        return node;
    }

    // ---------- layout ----------

    public static VBox vbox(double spacing, Node... children) {
        VBox b = new VBox(spacing, children);
        return b;
    }

    public static HBox hbox(double spacing, Node... children) {
        HBox b = new HBox(spacing, children);
        b.setAlignment(Pos.CENTER_LEFT);
        return b;
    }

    public static VBox panel(Node... children) {
        VBox p = new VBox(12, children);
        p.getStyleClass().add("panel");
        p.setMaxWidth(Double.MAX_VALUE);
        return p;
    }

    /** Two-column table: product name and quantity. */
    public static Node totalsTable(Map<String, Long> totals) {
        GridPane g = new GridPane();
        g.setHgap(40);
        g.setVgap(6);
        g.add(text("Product", "field-label"), 0, 0);
        g.add(text("Quantity", "field-label"), 1, 0);
        int row = 1;
        for (Map.Entry<String, Long> e : totals.entrySet()) {
            g.add(text(e.getKey()), 0, row);
            g.add(text(String.valueOf(e.getValue())), 1, row);
            row++;
        }
        return g;
    }

    public static Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    public static VBox labeled(String label, Node input) {
        return new VBox(3, text(label, "field-label"), input);
    }

    public static TextField field(String prompt, String initial) {
        TextField f = new TextField(initial == null ? "" : initial);
        f.setPromptText(prompt);
        return f;
    }

    public static TextArea area(String prompt, String initial, int rows) {
        TextArea a = new TextArea(initial == null ? "" : initial);
        a.setPromptText(prompt);
        a.setPrefRowCount(rows);
        a.setWrapText(true);
        return a;
    }

    // ---------- pictures ----------

    private static final Map<Long, Image> CACHE = new HashMap<>();

    public static Image image(Long id) {
        if (id == null) return null;
        Image cached = CACHE.get(id);
        if (cached != null) return cached;
        byte[] bytes = Dao.imageBytes(id);
        if (bytes == null) return null;
        Image img = new Image(new ByteArrayInputStream(bytes));
        CACHE.put(id, img);
        return img;
    }

    /** A rounded, centre-cropped photo, or a coloured placeholder with a letter when there is no photo. */
    public static StackPane photo(Long imageId, String initialText, double w, double h) {
        return photo(imageId, initialText, w, h, 23);
    }

    public static StackPane photo(Long imageId, String initialText, double w, double h, double radius) {
        StackPane box = new StackPane();
        box.setMinSize(w, h);
        box.setPrefSize(w, h);
        box.setMaxSize(w, h);

        Image img = image(imageId);
        if (img != null && !img.isError() && img.getWidth() > 0) {
            ImageView iv = new ImageView(img);
            iv.setFitWidth(w);
            iv.setFitHeight(h);
            double aspect = w / h;
            double iw = img.getWidth();
            double ih = img.getHeight();
            double vw;
            double vh;
            if (iw / ih > aspect) { vh = ih; vw = ih * aspect; }
            else { vw = iw; vh = iw / aspect; }
            iv.setViewport(new Rectangle2D((iw - vw) / 2, (ih - vh) / 2, vw, vh));
            box.getChildren().add(iv);
        } else {
            box.getStyleClass().add("placeholder");
            Label letter = display(initialText, "placeholder-letter");
            letter.setStyle(letter.getStyle() + "-fx-font-size: " + Math.max(18, Math.min(w, h) / 3.5) + "px;");
            box.getChildren().add(letter);
        }

        Rectangle clip = new Rectangle(w, h);
        clip.setArcWidth(radius * 2);
        clip.setArcHeight(radius * 2);
        box.setClip(clip);
        return box;
    }

    public static File pickImage() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Choose a photo");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Pictures", "*.jpg", "*.jpeg", "*.png", "*.gif", "*.bmp"));
        return fc.showOpenDialog(App.stage);
    }

    // ---------- icons (simple outline shapes in a 24 x 24 box) ----------

    public static Node icon(double size, Color stroke, String... paths) {
        Group g = new Group();
        for (String d : paths) {
            SVGPath p = new SVGPath();
            p.setContent(d);
            p.setFill(Color.TRANSPARENT);
            p.setStroke(stroke);
            p.setStrokeWidth(1.8);
            p.setStrokeLineCap(StrokeLineCap.ROUND);
            p.setStrokeLineJoin(StrokeLineJoin.ROUND);
            g.getChildren().add(p);
        }
        g.setScaleX(size / 24.0);
        g.setScaleY(size / 24.0);
        StackPane box = new StackPane(g);
        box.setMinSize(size, size);
        box.setPrefSize(size, size);
        box.setMaxSize(size, size);
        return box;
    }

    public static Node withBadge(Node node, int count) {
        StackPane s = new StackPane(node);
        if (count > 0) {
            Label b = text(count > 99 ? "99+" : String.valueOf(count), "badge");
            StackPane.setAlignment(b, Pos.TOP_RIGHT);
            StackPane.setMargin(b, new Insets(-4, -8, 0, 0));
            s.getChildren().add(b);
        }
        return s;
    }

    // ---------- dialogs ----------

    public static void info(String message) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        a.setHeaderText(null);
        a.initOwner(App.stage);
        a.showAndWait();
    }

    public static void error(String message) {
        Alert a = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        a.setHeaderText(null);
        a.initOwner(App.stage);
        a.showAndWait();
    }

    public static boolean confirm(String message) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        a.setHeaderText(null);
        a.initOwner(App.stage);
        return a.showAndWait().filter(b -> b == ButtonType.YES).isPresent();
    }

    /** Returns what the person typed, or null if they cancelled. */
    public static String ask(String title, String prompt) {
        TextInputDialog d = new TextInputDialog();
        d.setTitle(title);
        d.setHeaderText(null);
        d.setContentText(prompt);
        d.initOwner(App.stage);
        Optional<String> r = d.showAndWait();
        return r.orElse(null);
    }
}
