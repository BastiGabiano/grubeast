package com.grubeast;

import com.grubeast.Models.*;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;

/** The admin approves or rejects new businesses. */
public final class AdminView {
    private AdminView() {}

    static Node admin() {
        if (!Session.is(Models.ADMIN)) return Ui.panel(Ui.text("This page is for admins.", "muted"));

        VBox page = new VBox(18, Ui.display("ADMIN DASHBOARD", "display"));

        // pending
        VBox pending = Ui.panel(Ui.text("Pending business applications", "title-md"));
        List<User> waiting = Dao.businesses(false, false);
        if (waiting.isEmpty()) pending.getChildren().add(Ui.text("No pending applications.", "muted"));
        for (User b : waiting) {
            HBox row = new HBox(12,
                new VBox(2, Ui.text(b.name(), "title-sm"), Ui.text(b.email(), "muted")), Ui.spacer(),
                Ui.small(Ui.button("Approve", () -> {
                    Dao.approveBusiness(b.id());
                    App.admin();
                })),
                Ui.small(Ui.outline("Reject", () -> {
                    String reason = Ui.ask("Reject " + b.name(), "Reason (shown to the applicant):");
                    if (reason == null) return;
                    Dao.rejectBusiness(b.id(), reason);
                    App.admin();
                })),
                Ui.small(Ui.outline("Message", () -> App.chat(b.id()))));
            row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            row.getStyleClass().add("row-box");
            pending.getChildren().add(row);
        }

        // approved
        VBox approved = Ui.panel(Ui.text("Approved businesses", "title-md"));
        List<User> live = Dao.businesses(true, false);
        if (live.isEmpty()) approved.getChildren().add(Ui.text("None yet.", "muted"));
        for (User b : live) {
            approved.getChildren().add(Ui.hbox(10, Ui.text(b.name(), "title-sm"), Ui.text(b.email(), "muted")));
        }

        // rejected
        VBox rejected = Ui.panel(Ui.text("Rejected applications", "title-md"));
        List<User> turnedDown = Dao.businesses(false, true);
        if (turnedDown.isEmpty()) rejected.getChildren().add(Ui.text("None.", "muted"));
        for (User b : turnedDown) {
            HBox row = new HBox(12,
                new VBox(2, Ui.text(b.name(), "title-sm"), Ui.text(b.email(), "muted"),
                    Ui.text("Reason: " + (b.rejectionReason() == null ? "(no reason given)" : b.rejectionReason()))),
                Ui.spacer(),
                Ui.small(Ui.button("Approve instead", () -> {
                    Dao.approveBusiness(b.id());
                    App.admin();
                })));
            row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            row.getStyleClass().add("row-box");
            rejected.getChildren().add(row);
        }

        page.getChildren().addAll(pending, approved, rejected);
        return page;
    }
}
