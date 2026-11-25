package com.grubeast;

import com.grubeast.Models.User;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;
import java.util.function.Supplier;

/** The window, the red header, and every "go to this screen" method. */
public class App extends Application {

    static Stage stage;
    private static BorderPane root;
    private static ScrollPane scroller;
    private static String pendingMessage;
    private static boolean pendingOk;
    private static String lastSearch = "";

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primary) {
        stage = primary;
        pickFonts();

        scroller = new ScrollPane();
        scroller.setFitToWidth(true);
        scroller.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        root = new BorderPane();
        root.setCenter(scroller);
        root.setStyle("-fx-font-family: '" + Ui.bodyFont + "';");

        Scene scene = new Scene(root, 1280, 800);
        scene.getStylesheets().add(App.class.getResource("/style.css").toExternalForm());

        primary.setTitle("Grub East");
        primary.setScene(scene);
        primary.setMinWidth(1000);
        primary.setMinHeight(640);
        primary.show();

        try {
            Db.connect(Db.loadConfig());
            home("");
        } catch (Exception e) {
            databaseSettings(e.getMessage());
        }
    }

    /** Uses the Figma fonts when they are installed (or dropped into resources/fonts), otherwise safe fallbacks. */
    private static void pickFonts() {
        for (String file : new String[] {"InriaSans-Regular.ttf", "InriaSans-Bold.ttf", "InriaSans-Light.ttf"}) {
            try (InputStream in = App.class.getResourceAsStream("/fonts/" + file)) {
                if (in != null) Font.loadFont(in, 12);
            } catch (IOException ignored) {
            }
        }
        List<String> families = Font.getFamilies();
        Ui.bodyFont = families.contains("Inria Sans") ? "Inria Sans"
                    : families.contains("Segoe UI") ? "Segoe UI" : "System";
        Ui.displayFont = families.contains("Impact") ? "Impact"
                       : families.contains("Anton") ? "Anton" : Ui.bodyFont;
    }

    // =====================================================================
    // Showing a screen
    // =====================================================================

    /** A message that appears at the top of the next screen. */
    public static void flash(String message, boolean ok) {
        pendingMessage = message;
        pendingOk = ok;
    }

    private static void go(Supplier<Node> view) {
        try {
            ChatView.stopRefresh();   // leaving a chat stops its refresh timer
            show(view.get());
        } catch (Db.DbException e) {
            Ui.error(e.getMessage());
        }
    }

    private static void show(Node content) {
        Session.refresh();

        VBox page = new VBox(14);
        page.setPadding(new Insets(18, 28, 40, 28));
        if (pendingMessage != null) {
            page.getChildren().add(Ui.flash(pendingMessage, pendingOk));
            pendingMessage = null;
        }
        page.getChildren().add(content);

        root.setTop(chrome());
        scroller.setContent(page);
        scroller.setVvalue(0);
    }

    // =====================================================================
    // Header and menu
    // =====================================================================

    private static Node chrome() {
        User u = Session.user;
        boolean showCart = u == null || Session.isBuyer();

        Node avatar = Ui.clickable(
            Ui.icon(30, Color.web("#4F378A"), "M8 8a4 4 0 1 0 8 0a4 4 0 1 0 -8 0", "M4 21c0-4.4 3.6-7 8-7s8 2.6 8 7"),
            () -> { if (Session.user == null) login(); else profile(); });
        HBox avatarCircle = new HBox(avatar);
        avatarCircle.getStyleClass().add("avatar");
        avatarCircle.setAlignment(Pos.CENTER);
        avatarCircle.setMinSize(52, 52);
        avatarCircle.setPrefSize(52, 52);
        avatarCircle.setMaxSize(52, 52);
        Ui.clickable(avatarCircle, () -> { if (Session.user == null) login(); else profile(); });

        Label brand = Ui.display("GRUB EAST", "brand");
        Ui.clickable(brand, App::about);

        TextField search = new TextField(lastSearch);
        search.setPromptText("Search food and snacks");
        search.getStyleClass().add("search-field");
        HBox.setHgrow(search, Priority.ALWAYS);
        search.setOnAction(e -> home(search.getText()));

        Node menuIcon = Ui.icon(22, Color.web("#49454F"), "M4 7h16", "M4 12h16", "M4 17h16");
        Node magnifier = Ui.clickable(
            Ui.icon(22, Color.web("#49454F"), "M5 11a6 6 0 1 0 12 0a6 6 0 1 0 -12 0", "M16 16l5 5"),
            () -> home(search.getText()));
        HBox searchBox = new HBox(8, menuIcon, search, magnifier);
        searchBox.getStyleClass().add("search-box");
        searchBox.setAlignment(Pos.CENTER_LEFT);
        searchBox.setMinHeight(48);
        searchBox.setPrefHeight(48);
        searchBox.setMaxWidth(720);
        HBox.setHgrow(searchBox, Priority.ALWAYS);

        HBox header = new HBox(26, avatarCircle, brand, searchBox, Ui.spacer());
        if (showCart) {
            int count = (u != null) ? Dao.cartCount(u.id()) : 0;
            Node cart = Ui.clickable(Ui.icon(42, Color.WHITE,
                "M2 3h3l2.4 12h11l2.1-8H6", "M7.4 20a1.6 1.6 0 1 0 3.2 0a1.6 1.6 0 1 0 -3.2 0",
                "M15.4 20a1.6 1.6 0 1 0 3.2 0a1.6 1.6 0 1 0 -3.2 0"),
                () -> { if (Session.user == null) login(); else cart(); });
            header.getChildren().add(Ui.withBadge(cart, count));
        }
        int unread = (u != null) ? Dao.unread(u.id()) : 0;
        Node chat = Ui.clickable(Ui.icon(42, Color.WHITE, "M3 4h13v9H9l-4 3v-3H3z", "M8 17v1h8l3 2v-2h2V9h-3"),
            () -> { if (Session.user == null) login(); else messages(); });
        header.getChildren().add(Ui.withBadge(chat, unread));
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(10, 28, 10, 28));
        header.getStyleClass().add("header");

        // second row: links that depend on who is logged in
        HBox nav = new HBox(20);
        nav.setAlignment(Pos.CENTER_LEFT);
        nav.setPadding(new Insets(8, 28, 8, 28));
        nav.getStyleClass().add("subnav");
        nav.getChildren().addAll(
            Ui.link("Home", () -> home("")),
            Ui.link("Shops", () -> shops("")),
            Ui.link("Events", App::events));

        if (u == null) {
            nav.getChildren().addAll(Ui.link("Log in", App::login), Ui.link("Sign up", App::register));
        } else {
            nav.getChildren().add(Ui.link("My profile", App::profile));
            if (Session.isBuyer()) nav.getChildren().add(Ui.link("My preorders", App::myOrders));
            if (Session.liveBusiness()) {
                nav.getChildren().add(Ui.link("Manage my shop", () -> business("shop")));
                nav.getChildren().add(Ui.link("Preorders", () -> business("orders")));
            }
            if (Session.is(Models.ORG)) nav.getChildren().add(Ui.link("Manage my events", () -> org("events")));
            if (Session.is(Models.ADMIN)) nav.getChildren().add(Ui.link("Admin dashboard", App::admin));
            if (!Session.is(Models.ADMIN)) nav.getChildren().add(Ui.link("Contact admin", App::support));
            nav.getChildren().add(Ui.spacer());
            nav.getChildren().add(Ui.link("Log out (" + u.name() + ")", App::logout));
        }

        VBox top = new VBox(header, nav);
        if (u != null && u.role().equals(Models.BUSINESS) && !u.approved() && !u.rejected()) {
            top.getChildren().add(notice("Your shop is waiting for admin approval. It stays hidden until then.", "notice-warn"));
        } else if (u != null && u.role().equals(Models.BUSINESS) && u.rejected()) {
            top.getChildren().add(notice("Your business application was rejected. Open My profile to see why and reapply.", "notice-bad"));
        }
        return top;
    }

    private static Node notice(String text, String style) {
        Label l = Ui.wrap(text, style);
        VBox box = new VBox(l);
        box.setPadding(new Insets(10, 28, 0, 28));
        return box;
    }

    // =====================================================================
    // Screens
    // =====================================================================

    public static void home(String q) { lastSearch = q == null ? "" : q.trim(); go(() -> StoreView.home(lastSearch)); }
    public static void about() { go(StoreView::about); }
    public static void shops(String q) { go(() -> StoreView.shops(q)); }
    public static void product(long id) { go(() -> StoreView.product(id)); }
    public static void shop(long id) { go(() -> StoreView.shop(id)); }
    public static void cart() { go(StoreView::cart); }
    public static void myOrders() { go(StoreView::myOrders); }
    public static void events() { go(EventsView::events); }

    public static void login() { go(AuthView::login); }
    public static void register() { go(AuthView::register); }
    public static void profile() { go(ProfileView::profile); }

    public static void business(String section) { go(() -> BusinessView.view(section)); }
    public static void org(String section) { go(() -> OrgView.view(section, null)); }
    public static void orgEdit(long eventId) { go(() -> OrgView.view("events", eventId)); }
    public static void admin() { go(AdminView::admin); }

    public static void messages() { go(ChatView::inbox); }
    public static void chat(long userId) { go(() -> ChatView.chat(userId)); }

    public static void support() {
        User admin = Dao.firstAdmin();
        if (admin == null) { Ui.error("There is no admin account yet."); return; }
        chat(admin.id());
    }

    public static void logout() {
        Session.user = null;
        lastSearch = "";
        home("");
    }

    // =====================================================================
    // Database settings (shown when MySQL can't be reached)
    // =====================================================================

    public static void databaseSettings(String problem) {
        Properties p = Db.loadConfig();
        TextField host = Ui.field("localhost", p.getProperty("host"));
        TextField port = Ui.field("3306", p.getProperty("port"));
        TextField database = Ui.field("grubeast", p.getProperty("database"));
        TextField user = Ui.field("root", p.getProperty("user"));
        PasswordField password = new PasswordField();
        password.setText(p.getProperty("password", ""));
        Label result = Ui.wrap("");

        VBox form = Ui.panel(
            Ui.display("DATABASE SETTINGS", "display"),
            Ui.wrap("Grub East keeps everything in a MySQL database so every computer sees the same shops. "
                  + "Start MySQL first (in XAMPP, press Start next to MySQL), then connect below.", "muted"));
        if (problem != null && !problem.isBlank()) {
            form.getChildren().add(Ui.flash("Could not connect: " + problem, false));
        }
        form.getChildren().addAll(
            Ui.labeled("Server (host)", host), Ui.labeled("Port", port), Ui.labeled("Database name", database),
            Ui.labeled("User", user), Ui.labeled("Password", password), result,
            Ui.button("Save and connect", () -> {
                Properties np = new Properties();
                np.setProperty("host", host.getText().trim());
                np.setProperty("port", port.getText().trim());
                np.setProperty("database", database.getText().trim());
                np.setProperty("user", user.getText().trim());
                np.setProperty("password", password.getText());
                try {
                    Db.connect(np);
                    Db.saveConfig(np);
                    home("");
                } catch (Exception e) {
                    result.setText("Could not connect: " + e.getMessage());
                    result.getStyleClass().setAll("flash-err");
                }
            }));
        form.setMaxWidth(520);

        // no database yet, so show a plain header without asking the database anything
        HBox header = new HBox(Ui.display("GRUB EAST", "brand"));
        header.getStyleClass().add("header");
        header.setPadding(new Insets(10, 28, 10, 28));
        header.setAlignment(Pos.CENTER_LEFT);
        root.setTop(header);

        VBox page = new VBox(form);
        page.setPadding(new Insets(24, 28, 40, 28));
        scroller.setContent(page);
    }
}
