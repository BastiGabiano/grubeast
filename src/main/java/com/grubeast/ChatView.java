package com.grubeast;

import com.grubeast.Models.*;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.List;

/** Inbox and one-to-one chat. An open chat checks for new messages every few seconds. */
public final class ChatView {
    private ChatView() {}

    private static Timeline refresher;

    static void stopRefresh() {
        if (refresher != null) {
            refresher.stop();
            refresher = null;
        }
    }

    // ---------------------------------------------------------------------
    // Inbox
    // ---------------------------------------------------------------------

    static Node inbox() {
        if (Session.user == null) return Ui.panel(Ui.text("Log in to see your messages.", "muted"));
        User me = Session.user;

        VBox page = new VBox(14, Ui.display("MESSAGES", "display"));
        List<Conversation> list = Dao.inbox(me.id());
        if (list.isEmpty()) {
            VBox empty = Ui.panel(Ui.wrap("No conversations yet. Open a shop, product or event and press the message button.", "muted"));
            if (!me.role().equals(Models.ADMIN)) empty.getChildren().add(Ui.button("Contact the admin", App::support));
            page.getChildren().add(empty);
        }
        for (Conversation c : list) {
            User p = c.partner();
            HBox top = Ui.hbox(10, Ui.text(p.name(), "title-sm"), Ui.text("(" + p.roleLabel() + ")", "muted"));
            if (me.role().equals(Models.ADMIN)) top.getChildren().add(Ui.text(p.email(), "muted"));
            if (c.unread() > 0) top.getChildren().add(Ui.text("● " + c.unread() + " new", "stars"));

            String preview = c.last().body().length() > 90 ? c.last().body().substring(0, 90) + "…" : c.last().body();
            VBox box = new VBox(4, top,
                Ui.text((c.last().senderId() == me.id() ? "You: " : "") + preview),
                Ui.text(c.last().sentAt().format(Ui.DATE_TIME), "muted"));
            box.getStyleClass().add("panel");
            box.setOnMouseClicked(e -> App.chat(p.id()));
            box.setStyle("-fx-cursor: hand;");
            page.getChildren().add(box);
        }
        return page;
    }

    // ---------------------------------------------------------------------
    // One conversation
    // ---------------------------------------------------------------------

    static Node chat(long partnerId) {
        User me = Session.user;
        if (me == null) return Ui.panel(Ui.text("Log in to send messages.", "muted"));
        User partner = Dao.user(partnerId);
        if (partner == null || partner.id() == me.id()) return inbox();

        boolean canSend = Dao.canMessage(me, partner);
        if (!canSend && Dao.conversation(me.id(), partnerId).isEmpty()) {
            return Ui.panel(Ui.text("You can't send messages to this account.", "muted"));
        }

        VBox bubbles = new VBox(8);
        bubbles.setPadding(new Insets(10));
        ScrollPane scroll = new ScrollPane(bubbles);
        scroll.setFitToWidth(true);
        scroll.setMinHeight(420);
        scroll.setPrefHeight(420);
        scroll.setStyle("-fx-background-color: white; -fx-background-radius: 16; -fx-border-color: #dddddd; -fx-border-radius: 16;");

        int[] shown = {-1};
        Runnable reload = () -> {
            List<Message> msgs = Dao.conversation(me.id(), partnerId);
            Dao.markSeen(partnerId, me.id());
            if (msgs.size() == shown[0]) return;
            shown[0] = msgs.size();

            bubbles.getChildren().clear();
            if (msgs.isEmpty()) bubbles.getChildren().add(Ui.text("No messages yet. Say hi!", "muted"));
            for (Message m : msgs) {
                boolean mine = m.senderId() == me.id();
                Label bubble = Ui.wrap(m.body(), mine ? "bubble-me" : "bubble-them");
                bubble.setMaxWidth(460);
                Label when = Ui.text((mine ? "You" : partner.name()) + " - " + m.sentAt().format(Ui.DATE_TIME), "muted");
                when.setStyle("-fx-font-size: 12px;");
                VBox item = new VBox(2, bubble, when);
                item.setFillWidth(false);
                item.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
                bubbles.getChildren().add(item);
            }
            Platform.runLater(() -> scroll.setVvalue(1.0));
        };
        reload.run();

        VBox panel = Ui.panel(
            Ui.hbox(10, Ui.display("CHAT WITH " + partner.name().toUpperCase(), "display"), Ui.spacer(),
                    Ui.small(Ui.outline("Refresh", () -> { shown[0] = -1; reload.run(); }))),
            Ui.text(partner.roleLabel(), "muted"),
            scroll);

        if (canSend) {
            TextArea input = Ui.area("Write a message...", "", 3);
            Runnable send = () -> {
                String text = input.getText();
                if (text == null || text.isBlank()) return;
                Dao.sendMessage(me.id(), partnerId, text);
                input.clear();
                reload.run();
            };
            panel.getChildren().addAll(input, Ui.hbox(10, Ui.button("Send", send)));
        } else {
            panel.getChildren().add(Ui.text("You can't send new messages to this account.", "muted"));
        }

        stopRefresh();
        refresher = new Timeline(new KeyFrame(Duration.seconds(4), e -> {
            try {
                reload.run();
            } catch (Db.DbException ignored) {
                // try again on the next tick
            }
        }));
        refresher.setCycleCount(Timeline.INDEFINITE);
        refresher.play();
        return panel;
    }
}
