package com.grubeast;

import com.grubeast.Models.*;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/** The business owner's dashboard, with the left menu from the Figma design. */
public final class BusinessView {
    private BusinessView() {}

    static Node view(String section) {
        if (!Session.liveBusiness()) {
            return Ui.panel(Ui.text("Your shop has to be approved by an admin before you can manage it.", "muted"));
        }
        User me = Session.user;
        Shop shop = Dao.shopOf(me.id());

        VBox side = new VBox(4);
        side.getStyleClass().add("sidebar");
        side.setMinWidth(230);
        side.setPrefWidth(230);
        side.setMaxWidth(230);
        side.getChildren().add(Ui.text(me.name(), "title-sm"));
        String[][] items = {
            {"shop", "Shop Details"}, {"products", "My Products"}, {"orders", "My Orders"},
            {"sponsor", "Sponsorships"}, {"reviews", "Review Management"}
        };
        for (String[] it : items) {
            Button b = new Button(it[1]);
            b.getStyleClass().add("side-item");
            if (it[0].equals(section)) b.getStyleClass().add("side-item-active");
            b.setMaxWidth(Double.MAX_VALUE);
            b.setOnAction(e -> App.business(it[0]));
            side.getChildren().add(b);
        }
        Button chat = new Button("Chat Management");
        chat.getStyleClass().add("side-item");
        chat.setMaxWidth(Double.MAX_VALUE);
        chat.setOnAction(e -> App.messages());
        side.getChildren().add(chat);

        Node content = switch (section) {
            case "products" -> products(shop);
            case "orders" -> orders(shop);
            case "sponsor" -> sponsorships(me);
            case "reviews" -> reviews(shop);
            default -> details(shop, me);
        };

        HBox layout = new HBox(18, side, content);
        layout.setAlignment(Pos.TOP_LEFT);
        HBox.setHgrow(content, Priority.ALWAYS);
        return layout;
    }

    // ---------------------------------------------------------------------
    // Shop details + preorder switch
    // ---------------------------------------------------------------------

    private static Node details(Shop shop, User me) {
        Button upload = Ui.outline("Upload logo", () -> {
            File f = Ui.pickImage();
            if (f == null) return;
            try {
                Dao.setLogo(shop.id(), Dao.saveImage(f));
                App.business("shop");
            } catch (IOException ex) {
                Ui.error(ex.getMessage());
            }
        });

        TextArea description = Ui.area("Tell students what you sell", shop.description(), 4);
        TextField location = Ui.field("Where students can find you", shop.location());
        TextField days = Ui.field("e.g. Mon-Fri, or Tue and Thu", shop.availableDays());
        TextField open = Ui.field("08:00", shop.openTime());
        TextField close = Ui.field("17:00", shop.closeTime());

        VBox box = Ui.panel(
            Ui.text("Shop details", "title-md"),
            Ui.hbox(16, Ui.photo(shop.logoId(), Ui.initial(me.name()), 90, 90), upload),
            Ui.labeled("Description", description),
            Ui.labeled("Location", location),
            Ui.labeled("Available days", days),
            Ui.hbox(16, Ui.labeled("Opens", open), Ui.labeled("Closes", close)),
            Ui.button("Save shop details", () -> {
                Dao.saveShopDetails(shop.id(), description.getText(), location.getText(), days.getText(),
                                    open.getText(), close.getText());
                App.flash("Shop details saved.", true);
                App.business("shop");
            }));

        VBox preorder = Ui.panel(
            Ui.text("Preorders", "title-md"),
            Ui.text("Preorders are currently " + (shop.allowPreorder() ? "ON" : "OFF") + ".", "title-sm"),
            Ui.wrap("When preorders are on, students can order ahead and you can see exactly how much to prepare.", "muted"),
            Ui.button(shop.allowPreorder() ? "Turn preorders off" : "Turn preorders on", () -> {
                Dao.setPreorder(shop.id(), !shop.allowPreorder());
                App.business("shop");
            }));

        return new VBox(18, box, preorder);
    }

    // ---------------------------------------------------------------------
    // Products
    // ---------------------------------------------------------------------

    private static Node products(Shop shop) {
        long ownerId = Session.user.id();
        VBox list = Ui.panel(Ui.text("My products", "title-md"));
        List<Product> products = Dao.productsOf(shop.id());
        if (products.isEmpty()) list.getChildren().add(Ui.text("No products yet. Add your first one below.", "muted"));

        for (Product p : products) {
            VBox text = new VBox(2, Ui.text(p.name(), "title-sm"),
                Ui.text(Ui.peso(p.price()) + (p.available() ? "" : "   (sold out)"), "muted"));
            if (p.description() != null) text.getChildren().add(Ui.text(p.description().length() > 70 ? p.description().substring(0, 70) + "…" : p.description(), "muted"));

            Button photoBtn = Ui.small(Ui.outline(p.imageId() == null ? "Add photo" : "Change photo", () -> {
                File f = Ui.pickImage();
                if (f == null) return;
                try {
                    Dao.setProductImage(p.id(), ownerId, Dao.saveImage(f));
                    App.business("products");
                } catch (IOException ex) {
                    Ui.error(ex.getMessage());
                }
            }));
            Button toggle = Ui.small(Ui.outline(p.available() ? "Mark sold out" : "Mark available", () -> {
                Dao.toggleProduct(p.id(), ownerId);
                App.business("products");
            }));
            Button delete = Ui.small(Ui.button("Delete", () -> {
                if (Ui.confirm("Delete \"" + p.name() + "\"?")) {
                    Dao.deleteProduct(p.id(), ownerId);
                    App.business("products");
                }
            }));

            HBox row = new HBox(14, Ui.photo(p.imageId(), Ui.initial(p.name()), 64, 64, 12), text, Ui.spacer(), photoBtn, toggle, delete);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("row-box");
            list.getChildren().add(row);
        }

        // add form
        TextField name = Ui.field("Product name", "");
        TextField price = Ui.field("55 or 2.50", "");
        TextArea description = Ui.area("Optional", "", 3);
        File[] chosen = new File[1];
        Label chosenLabel = Ui.text("No photo chosen", "muted");
        Button choose = Ui.outline("Choose photo", () -> {
            File f = Ui.pickImage();
            if (f != null) {
                chosen[0] = f;
                chosenLabel.setText(f.getName());
            }
        });

        VBox form = Ui.panel(
            Ui.text("Add a product", "title-md"),
            Ui.labeled("Name", name),
            Ui.labeled("Price (₱)", price),
            Ui.labeled("Description", description),
            Ui.hbox(12, choose, chosenLabel),
            Ui.button("Add product", () -> {
                String n = name.getText().trim();
                if (n.isEmpty()) { Ui.error("Please enter a product name."); return; }
                BigDecimal amount;
                try {
                    amount = new BigDecimal(price.getText().trim());
                } catch (NumberFormatException ex) {
                    Ui.error("Please enter the price as a number, like 55 or 2.50.");
                    return;
                }
                if (amount.signum() < 0 || amount.compareTo(new BigDecimal("99999999")) > 0) {
                    Ui.error("That price doesn't look right.");
                    return;
                }
                Long imageId = null;
                String warning = null;
                if (chosen[0] != null) {
                    try {
                        imageId = Dao.saveImage(chosen[0]);
                    } catch (IOException ex) {
                        warning = ex.getMessage() + " The product was added without a photo.";
                    }
                }
                Dao.addProduct(shop.id(), n, amount.setScale(2, RoundingMode.HALF_UP), description.getText(), imageId);
                App.flash(warning == null ? "Product added." : warning, warning == null);
                App.business("products");
            }));

        return new VBox(18, list, form);
    }

    // ---------------------------------------------------------------------
    // Orders: what to prepare, active requests, done
    // ---------------------------------------------------------------------

    private static String chip(String status) {
        return switch (status) {
            case Models.PENDING -> "Pending";
            case Models.CONFIRMED -> "Confirmed";
            case Models.READY -> "Ready for pickup";
            case Models.CLAIMED -> "Claimed";
            default -> "Cancelled";
        };
    }

    private static Node orders(Shop shop) {
        long ownerId = Session.user.id();
        List<Preorder> all = Dao.preordersOfShop(shop.id());

        VBox prep = Ui.panel(
            Ui.text("Preorders are " + (shop.allowPreorder() ? "ON" : "OFF"), "title-md"),
            Ui.text("To prepare", "title-sm"),
            Ui.text("Totals from pending and confirmed orders.", "muted"));
        Map<String, Long> totals = Dao.toPrepare(shop.id());
        prep.getChildren().add(totals.isEmpty() ? Ui.text("Nothing to prepare right now.") : Ui.totalsTable(totals));

        Map<String, Map<String, Long>> byEvent = Dao.toPrepareByEvent(shop.id());
        prep.getChildren().add(Ui.text("By event", "title-sm"));
        if (byEvent.isEmpty()) prep.getChildren().add(Ui.text("No event orders right now.", "muted"));
        for (Map.Entry<String, Map<String, Long>> e : byEvent.entrySet()) {
            prep.getChildren().addAll(Ui.text(e.getKey(), "title-sm"), Ui.totalsTable(e.getValue()));
        }

        VBox active = Ui.panel(Ui.text("Active requests", "title-md"),
            Ui.wrap("Mark an order claimed once the customer has picked it up.", "muted"));
        VBox done = Ui.panel(Ui.text("Done", "title-md"),
            Ui.wrap("Claimed orders count toward each product's \"sold\" number.", "muted"));
        boolean anyActive = false;
        boolean anyDone = false;
        for (Preorder o : all) {
            boolean isActive = o.status().equals(Models.PENDING) || o.status().equals(Models.CONFIRMED) || o.status().equals(Models.READY);
            (isActive ? active : done).getChildren().add(orderCard(o, ownerId, isActive));
            if (isActive) anyActive = true; else anyDone = true;
        }
        if (!anyActive) active.getChildren().add(Ui.text("No active requests.", "muted"));
        if (!anyDone) done.getChildren().add(Ui.text("Nothing here yet.", "muted"));

        return new VBox(18, prep, active, done);
    }

    private static Node orderCard(Preorder o, long ownerId, boolean active) {
        VBox box = new VBox(6);
        box.getStyleClass().add("row-box");
        box.getChildren().add(Ui.hbox(12, Ui.text(o.customerName(), "title-sm"),
            Ui.text(o.createdAt().format(Ui.DATE_TIME), "muted"), Ui.text(chip(o.status()), "status-chip")));
        if (o.eventTitle() != null) {
            box.getChildren().add(Ui.text("For event: " + o.eventTitle() + " (" + o.eventDate().format(Ui.DATE) + ")"));
        }
        for (Item i : o.items()) box.getChildren().add(Ui.text(i.quantity() + " x " + i.name()));
        box.getChildren().add(Ui.text("Total: " + Ui.peso(o.total()), "title-sm"));
        if (o.note() != null) box.getChildren().add(Ui.text("Note: " + o.note(), "muted"));

        if (active) {
            HBox actions = new HBox(8);
            String s = o.status();
            if (s.equals(Models.PENDING)) actions.getChildren().add(action("Confirm", o, ownerId, "confirm", false));
            if (s.equals(Models.CONFIRMED)) actions.getChildren().add(action("Mark ready", o, ownerId, "ready", false));
            if (s.equals(Models.CONFIRMED) || s.equals(Models.READY)) actions.getChildren().add(action("Mark claimed", o, ownerId, "claim", false));
            if (s.equals(Models.PENDING) || s.equals(Models.CONFIRMED)) actions.getChildren().add(action("Cancel", o, ownerId, "cancel", true));
            actions.getChildren().add(Ui.small(Ui.outline("Message customer", () -> App.chat(o.customerId()))));
            box.getChildren().add(actions);
        }
        return box;
    }

    private static Button action(String label, Preorder o, long ownerId, String what, boolean outline) {
        Runnable run = () -> {
            Dao.shopAction(o.id(), ownerId, what);
            App.business("orders");
        };
        return Ui.small(outline ? Ui.outline(label, run) : Ui.button(label, run));
    }

    // ---------------------------------------------------------------------
    // Sponsorships and reviews
    // ---------------------------------------------------------------------

    private static Node sponsorships(User me) {
        VBox box = Ui.panel(Ui.text("Sponsorships", "title-md"),
            Ui.wrap("Apply to sponsor events from the Events page. Your applications and their answers show up here.", "muted"),
            Ui.button("Browse events", App::events));
        List<Sponsor> mine = Dao.applicationsOfBusiness(me.id());
        if (mine.isEmpty()) box.getChildren().add(Ui.text("You haven't applied to any events yet.", "muted"));
        for (Sponsor s : mine) {
            String status = s.status().equals("ACCEPTED") ? "Accepted" : s.status().equals("DECLINED") ? "Declined" : "Pending";
            VBox row = new VBox(2, Ui.text(s.eventTitle() + "  (" + s.eventDate().format(Ui.DATE) + ")", "title-sm"),
                Ui.text("Status: " + status));
            row.getStyleClass().add("row-box");
            box.getChildren().add(row);
        }
        return box;
    }

    private static Node reviews(Shop shop) {
        RatingInfo r = Dao.rating(shop.id());
        VBox box = Ui.panel(Ui.text("Review Management", "title-md"),
            Ui.text(r.count() == 0 ? "No reviews yet." : String.format("%.1f", r.average()) + " " + Ui.stars(r.average())
                    + "  (" + r.count() + (r.count() == 1 ? " review)" : " reviews)"), "title-sm"));
        for (Review rv : Dao.reviews(shop.id())) {
            VBox row = new VBox(2, Ui.hbox(10, Ui.text(Ui.stars(rv.rating()), "stars"), Ui.text(rv.authorName(), "title-sm"),
                Ui.text(rv.postedAt().format(Ui.DATE), "muted")));
            if (rv.body() != null) row.getChildren().add(Ui.wrap(rv.body()));
            row.getStyleClass().add("row-box");
            box.getChildren().add(row);
        }
        return box;
    }
}
