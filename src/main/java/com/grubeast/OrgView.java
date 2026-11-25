package com.grubeast;

import com.grubeast.Models.*;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** The student organization's dashboard: events (with photos), orders for events, sponsor applications. */
public final class OrgView {
    private OrgView() {}

    static Node view(String section, Long editId) {
        if (!Session.is(Models.ORG)) {
            return Ui.panel(Ui.text("This page is for student organizations.", "muted"));
        }
        User me = Session.user;

        VBox side = new VBox(4);
        side.getStyleClass().add("sidebar");
        side.setMinWidth(230);
        side.setPrefWidth(230);
        side.setMaxWidth(230);
        side.getChildren().add(Ui.text(me.name(), "title-sm"));
        String[][] items = {{"events", "My Events"}, {"orders", "Orders for my events"}, {"sponsors", "Sponsor applications"}};
        for (String[] it : items) {
            Button b = new Button(it[1]);
            b.getStyleClass().add("side-item");
            if (it[0].equals(section)) b.getStyleClass().add("side-item-active");
            b.setMaxWidth(Double.MAX_VALUE);
            b.setOnAction(e -> App.org(it[0]));
            side.getChildren().add(b);
        }
        Button chat = new Button("Chat Management");
        chat.getStyleClass().add("side-item");
        chat.setMaxWidth(Double.MAX_VALUE);
        chat.setOnAction(e -> App.messages());
        side.getChildren().add(chat);

        Node content = switch (section) {
            case "orders" -> orders(me);
            case "sponsors" -> sponsors(me);
            default -> events(me, editId);
        };

        HBox layout = new HBox(18, side, content);
        layout.setAlignment(Pos.TOP_LEFT);
        HBox.setHgrow(content, Priority.ALWAYS);
        return layout;
    }

    // ---------------------------------------------------------------------
    // Events: list + create / edit form
    // ---------------------------------------------------------------------

    private static Node events(User me, Long editId) {
        List<Event> mine = Dao.eventsOf(me.id());
        Event editing = null;
        if (editId != null) {
            for (Event e : mine) if (e.id() == editId) editing = e;
        }

        VBox list = Ui.panel(Ui.text("My events", "title-md"));
        if (mine.isEmpty()) list.getChildren().add(Ui.text("You haven't posted any events yet. Add your first one below.", "muted"));
        for (Event e : mine) {
            VBox text = new VBox(2, Ui.text(e.title(), "title-sm"),
                Ui.text(e.date().format(Ui.DATE) + timeRange(e) + (e.location() == null ? "" : "  ·  " + e.location()), "muted"),
                Ui.text(e.seekingSponsors() ? "Looking for sponsors" : "Not looking for sponsors", e.seekingSponsors() ? "stars" : "muted"));
            Button edit = Ui.small(Ui.outline("Edit", () -> App.orgEdit(e.id())));
            Button delete = Ui.small(Ui.button("Delete", () -> {
                if (Ui.confirm("Delete \"" + e.title() + "\"? Orders for it stay, but won't be linked to the event.")) {
                    Dao.deleteEvent(e.id(), me.id());
                    App.org("events");
                }
            }));
            HBox row = new HBox(14, Ui.photo(e.imageId(), Ui.initial(e.title()), 96, 72, 12), text, Ui.spacer(), edit, delete);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("row-box");
            list.getChildren().add(row);
        }

        // ---- form ----
        final Event ev = editing;
        TextField title = Ui.field("Event name", ev == null ? "" : ev.title());
        TextArea description = Ui.area("What is the event about?", ev == null ? "" : ev.description(), 3);
        DatePicker date = new DatePicker(ev == null ? null : ev.date());
        date.setEditable(false);
        TextField start = Ui.field("e.g. 13:00", ev == null ? "" : ev.startTime());
        TextField end = Ui.field("e.g. 17:00", ev == null ? "" : ev.endTime());
        TextField location = Ui.field("Where is it?", ev == null ? "" : ev.location());
        CheckBox seeking = new CheckBox("We're looking for sponsors");
        seeking.setSelected(ev != null && ev.seekingSponsors());

        File[] chosen = new File[1];
        Label chosenLabel = Ui.text(ev != null && ev.imageId() != null ? "Keeping the current photo" : "No photo chosen", "muted");
        Button choose = Ui.outline("Choose photo", () -> {
            File f = Ui.pickImage();
            if (f != null) {
                chosen[0] = f;
                chosenLabel.setText(f.getName());
            }
        });

        VBox form = Ui.panel(
            Ui.text(ev == null ? "Post a new event" : "Edit event", "title-md"),
            Ui.labeled("Event name", title),
            Ui.labeled("Description", description),
            Ui.hbox(16, Ui.labeled("Date", date), Ui.labeled("Starts", start), Ui.labeled("Ends", end)),
            Ui.labeled("Location", location),
            seeking,
            Ui.hbox(12, choose, chosenLabel));
        Button save = Ui.button(ev == null ? "Post event" : "Save changes", () -> {
            LocalDate d = date.getValue();
            if (title.getText().isBlank() || d == null) {
                Ui.error("Please enter an event name and pick a date.");
                return;
            }
            Long newImage = null;
            if (chosen[0] != null) {
                try {
                    newImage = Dao.saveImage(chosen[0]);
                } catch (IOException ex) {
                    Ui.error(ex.getMessage());
                    return;
                }
            }
            Dao.saveEvent(ev == null ? null : ev.id(), me.id(), title.getText(), description.getText(), d,
                          start.getText(), end.getText(), location.getText(), seeking.isSelected(), newImage);
            App.flash(ev == null ? "Event posted." : "Event updated.", true);
            App.org("events");
        });
        HBox buttons = new HBox(10, save);
        if (ev != null) buttons.getChildren().add(Ui.outline("Cancel", () -> App.org("events")));
        form.getChildren().add(buttons);

        return new VBox(18, list, form);
    }

    private static String timeRange(Event e) {
        if (e.startTime() == null) return "";
        return "  ·  " + e.startTime() + (e.endTime() == null ? "" : " to " + e.endTime());
    }

    // ---------------------------------------------------------------------
    // Orders for my events
    // ---------------------------------------------------------------------

    private static Node orders(User me) {
        VBox page = new VBox(18);
        VBox intro = Ui.panel(Ui.text("Orders for my events", "title-md"),
            Ui.wrap("What you've ordered from student shops for each event. Cancelled orders aren't counted. "
                  + "To order, add items to your cart and choose the event at checkout.", "muted"),
            Ui.button("Browse products", () -> App.home("")));
        page.getChildren().add(intro);

        boolean any = false;
        for (Event e : Dao.eventsOf(me.id())) {
            List<Preorder> orders = Dao.preordersOfEvent(e.id());
            if (orders.isEmpty()) continue;
            any = true;

            VBox box = Ui.panel(Ui.text(e.title() + "  (" + e.date().format(Ui.DATE) + ")", "title-md"),
                Ui.text("Total ordered", "title-sm"));
            Map<String, Long> totals = Dao.eventOrderTotals(e.id());
            box.getChildren().add(totals.isEmpty() ? Ui.text("Everything was cancelled.", "muted") : Ui.totalsTable(totals));
            box.getChildren().add(Ui.text("Orders", "title-sm"));
            for (Preorder o : orders) {
                VBox row = new VBox(4, Ui.hbox(12, Ui.link(o.shopName(), () -> App.shop(o.shopId())),
                    Ui.text(StoreView.statusText(o.status()), "status-chip")));
                for (Item i : o.items()) row.getChildren().add(Ui.text(i.quantity() + " x " + i.name()));
                row.getChildren().add(Ui.text("Total: " + Ui.peso(o.total()), "title-sm"));
                row.getStyleClass().add("row-box");
                box.getChildren().add(row);
            }
            page.getChildren().add(box);
        }
        if (!any) page.getChildren().add(Ui.panel(Ui.text("No orders for your events yet.", "muted")));
        return page;
    }

    // ---------------------------------------------------------------------
    // Sponsor applications
    // ---------------------------------------------------------------------

    private static Node sponsors(User me) {
        VBox page = new VBox(18, Ui.panel(Ui.text("Sponsor applications", "title-md"),
            Ui.wrap("Businesses apply to sponsor events that are marked \"looking for sponsors\". Accepted sponsors are shown on the event.", "muted")));

        boolean any = false;
        for (Event e : Dao.eventsOf(me.id())) {
            List<Sponsor> apps = Dao.applicationsForEvent(e.id());
            if (apps.isEmpty()) continue;
            any = true;
            VBox box = Ui.panel(Ui.text(e.title() + "  (" + e.date().format(Ui.DATE) + ")", "title-md"));
            for (Sponsor a : apps) {
                VBox row = new VBox(4, Ui.hbox(12, Ui.text(a.businessName(), "title-sm"), Ui.text(a.businessEmail(), "muted"),
                    Ui.text(a.status().equals("ACCEPTED") ? "Accepted" : a.status().equals("DECLINED") ? "Declined" : "Pending", "status-chip")),
                    Ui.wrap(a.message() == null ? "(no message)" : a.message()));
                HBox actions = new HBox(8);
                if (!a.status().equals("ACCEPTED")) {
                    actions.getChildren().add(Ui.small(Ui.button("Accept", () -> {
                        Dao.respondToSponsor(a.id(), me.id(), true);
                        App.org("sponsors");
                    })));
                }
                if (!a.status().equals("DECLINED")) {
                    actions.getChildren().add(Ui.small(Ui.outline("Decline", () -> {
                        Dao.respondToSponsor(a.id(), me.id(), false);
                        App.org("sponsors");
                    })));
                }
                actions.getChildren().add(Ui.small(Ui.outline("Message", () -> App.chat(a.businessId()))));
                row.getChildren().add(actions);
                row.getStyleClass().add("row-box");
                box.getChildren().add(row);
            }
            page.getChildren().add(box);
        }
        if (!any) page.getChildren().add(Ui.panel(Ui.text("No sponsor applications yet.", "muted")));
        return page;
    }
}
