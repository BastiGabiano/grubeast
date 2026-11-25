package com.grubeast;

import com.grubeast.Models.User;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

/** Log in and sign up screens. */
public final class AuthView {
    private AuthView() {}

    static Node login() {
        TextField email = Ui.field("you@school.edu", "");
        PasswordField password = new PasswordField();
        password.setPromptText("Password");
        Label problem = new Label();

        Runnable submit = () -> {
            User u = Dao.login(email.getText(), password.getText());
            if (u == null) {
                problem.setText("Wrong email or password.");
                problem.getStyleClass().setAll("flash-err");
                return;
            }
            Session.user = u;
            if (u.role().equals(Models.ADMIN)) App.admin();
            else App.home("");
        };
        password.setOnAction(e -> submit.run());
        email.setOnAction(e -> password.requestFocus());

        VBox box = Ui.panel(
            Ui.display("LOG IN", "display"),
            Ui.labeled("Email", email),
            Ui.labeled("Password", password),
            problem,
            Ui.button("Log in", submit),
            Ui.hbox(6, Ui.text("No account yet?", "muted"), Ui.link("Sign up", App::register)),
            Ui.hbox(6, Ui.link("Database settings", () -> App.databaseSettings(null))));
        box.setMaxWidth(440);
        return box;
    }

    static Node register() {
        ComboBox<String> type = new ComboBox<>();
        type.getItems().addAll("A student looking for products", "A student business owner", "A student organization");
        type.getSelectionModel().selectFirst();
        type.setMaxWidth(Double.MAX_VALUE);

        TextField name = Ui.field("Your name, shop name or organization name", "");
        TextField email = Ui.field("you@school.edu", "");
        PasswordField password = new PasswordField();
        password.setPromptText("At least 6 characters");
        Label problem = new Label();

        VBox box = Ui.panel(
            Ui.display("CREATE AN ACCOUNT", "display"),
            Ui.labeled("I am signing up as", type),
            Ui.labeled("Name (or shop / organization name)", name),
            Ui.labeled("Email", email),
            Ui.labeled("Password", password),
            problem,
            Ui.button("Sign up", () -> {
                int i = type.getSelectionModel().getSelectedIndex();
                String role = i == 1 ? Models.BUSINESS : i == 2 ? Models.ORG : Models.USER;
                String error = Dao.register(name.getText(), email.getText(), password.getText(), role);
                if (error != null) {
                    problem.setText(error);
                    problem.getStyleClass().setAll("flash-err");
                    return;
                }
                User u = Dao.login(email.getText(), password.getText());
                Session.user = u;
                if (role.equals(Models.BUSINESS)) {
                    App.flash("Application received! Your shop stays hidden until an admin approves it.", true);
                } else {
                    App.flash("Account created. Welcome to Grub East!", true);
                }
                App.home("");
            }),
            Ui.hbox(6, Ui.text("Already have an account?", "muted"), Ui.link("Log in", App::login)));
        box.setMaxWidth(520);
        return box;
    }
}
