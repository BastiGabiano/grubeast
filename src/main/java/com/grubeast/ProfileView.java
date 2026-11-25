package com.grubeast;

import com.grubeast.Models.*;
import javafx.scene.Node;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

/** My profile: edit details, change password, and (for businesses) see the approval status. */
public final class ProfileView {
    private ProfileView() {}

    static Node profile() {
        if (Session.user == null) return Ui.panel(Ui.text("Log in to see your profile.", "muted"));
        User u = Session.user;

        VBox info = Ui.panel(
            Ui.display("MY PROFILE", "display"),
            Ui.text("Email: " + u.email()),
            Ui.text("Account type: " + u.roleLabel()));

        if (u.role().equals(Models.BUSINESS)) {
            if (u.approved() && !u.rejected()) {
                info.getChildren().add(Ui.text("Status: Approved. Your shop is live.", "stars"));
                info.getChildren().add(Ui.button("Manage my shop", () -> App.business("shop")));
            } else if (u.rejected()) {
                info.getChildren().add(Ui.text("Status: Rejected.", "title-sm"));
                info.getChildren().add(Ui.text("Reason: " + (u.rejectionReason() == null ? "(no reason given)" : u.rejectionReason())));
                info.getChildren().add(Ui.button("Reapply", () -> {
                    Dao.reapply(u.id());
                    App.flash("Your application is back in the admin's pending list.", true);
                    App.profile();
                }));
            } else {
                info.getChildren().add(Ui.text("Status: Waiting for admin approval.", "title-sm"));
            }
        }
        if (u.role().equals(Models.ORG)) {
            info.getChildren().add(Ui.button("Post or edit my events", () -> App.org("events")));
        }

        // edit details
        TextField name = Ui.field("Name", u.name());
        VBox edit = Ui.panel(Ui.text("Edit profile", "title-md"),
            Ui.labeled(u.role().equals(Models.BUSINESS) ? "Shop name" : u.role().equals(Models.ORG) ? "Organization name" : "Name", name));
        TextArea bio = Ui.area("About your organization", u.bio(), 4);
        TextField contact = Ui.field("Email, Facebook page, etc.", u.contact());
        if (u.role().equals(Models.ORG)) {
            edit.getChildren().addAll(Ui.labeled("About your organization", bio), Ui.labeled("Contact", contact));
        }
        edit.getChildren().add(Ui.button("Save", () -> {
            if (name.getText().isBlank()) { Ui.error("Please enter a name."); return; }
            boolean org = u.role().equals(Models.ORG);
            Dao.updateProfile(u.id(), name.getText(), org ? bio.getText() : u.bio(), org ? contact.getText() : u.contact());
            App.flash("Profile saved.", true);
            App.profile();
        }));

        // password
        PasswordField oldPw = new PasswordField();
        PasswordField newPw = new PasswordField();
        newPw.setPromptText("At least 6 characters");
        VBox password = Ui.panel(Ui.text("Change password", "title-md"),
            Ui.labeled("Current password", oldPw), Ui.labeled("New password", newPw),
            Ui.button("Change password", () -> {
                if (newPw.getText().length() < 6) { Ui.error("The new password must be at least 6 characters."); return; }
                if (Dao.changePassword(u.id(), oldPw.getText(), newPw.getText())) {
                    App.flash("Password changed.", true);
                    App.profile();
                } else {
                    Ui.error("The current password is wrong.");
                }
            }));

        VBox page = new VBox(18, info, edit, password);
        page.setMaxWidth(760);
        return page;
    }
}
