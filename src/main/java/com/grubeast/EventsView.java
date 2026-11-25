package com.grubeast;

import com.grubeast.Models.*;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;

/** Public list of upcoming events, with sponsor applications for businesses. */
public final class EventsView {
    private EventsView() {}

    static Node events() {
        VBox page = new VBox(14, Ui.display("UPCOMING EVENTS", "display"));
        List<Event> list = Dao.upcomingEvents();
        if (list.isEmpty()) page.getChildren().add(Ui.panel(Ui.text("No upcoming events yet. Check back soon!", "muted")));

        for (Event e : list) {
            VBox info = new VBox(6, Ui.wrap(e.title(), "title-md"));
            HBox hosted = Ui.hbox(10, Ui.text("Hosted by " + e.organizerName(), "title-sm"));
            if (Session.user != null && Session.user.id() != e.organizerId()
                    && Dao.canMessage(Session.user, Dao.user(e.organizerId()))) {
                hosted.getChildren().add(Ui.small(Ui.outline("Message organizer", () -> App.chat(e.organizerId()))));
            }
            info.getChildren().add(hosted);
            if (e.seekingSponsors()) info.getChildren().add(Ui.text("Looking for sponsors", "stars"));

            String when = e.date().format(Ui.DATE);
            if (e.startTime() != null) when += "  ·  " + e.startTime() + (e.endTime() == null ? "" : " to " + e.endTime());
            info.getChildren().add(Ui.text(when));
            if (e.location() != null) info.getChildren().add(Ui.text("Location: " + e.location(), "muted"));
            if (e.description() != null) info.getChildren().add(Ui.wrap(e.description()));
            if (e.organizerContact() != null) info.getChildren().add(Ui.text("Contact: " + e.organizerContact(), "muted"));

            List<Sponsor> accepted = Dao.acceptedSponsors(e.id());
            if (!accepted.isEmpty()) {
                StringBuilder names = new StringBuilder();
                for (Sponsor s : accepted) {
                    if (names.length() > 0) names.append(", ");
                    names.append(s.businessName());
                }
                info.getChildren().add(Ui.wrap("Sponsored by: " + names, "title-sm"));
            }

            if (e.seekingSponsors()) info.getChildren().add(sponsorSection(e));

            HBox card = new HBox(20, Ui.photo(e.imageId(), Ui.initial(e.title()), 300, 200), info);
            HBox.setHgrow(info, Priority.ALWAYS);
            card.getStyleClass().add("panel");
            page.getChildren().add(card);
        }
        return page;
    }

    private static Node sponsorSection(Event e) {
        if (Session.user == null) {
            return Ui.hbox(6, Ui.link("Log in", App::login), Ui.text("as a business to apply as a sponsor.", "muted"));
        }
        if (!Session.liveBusiness()) return new VBox();

        long uid = Session.user.id();
        Sponsor mine = Dao.myApplication(e.id(), uid);
        if (mine != null) {
            String status = mine.status().equals("ACCEPTED") ? "Accepted!" : mine.status().equals("DECLINED") ? "Declined" : "Pending review";
            return Ui.text("Your application: " + status, "title-sm");
        }
        TextArea message = Ui.area("What can you offer? (optional)", "", 2);
        VBox box = new VBox(6, Ui.text("Apply to sponsor this event", "title-sm"), message,
            Ui.button("Apply to sponsor", () -> {
                String error = Dao.applyToSponsor(e.id(), uid, message.getText());
                App.flash(error == null ? "Application sent! The organization will review it." : error, error == null);
                App.events();
            }));
        box.getStyleClass().add("row-box");
        return box;
    }
}
