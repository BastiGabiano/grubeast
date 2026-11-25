package com.grubeast;

import com.grubeast.Models.*;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** What students and organizations see: home, product page, shop page, cart and their preorders. */
public final class StoreView {
    private StoreView() {}

    // =====================================================================
    // Home: Trending + all products
    // =====================================================================

    static Node home(String q) {
        String query = q == null ? "" : q.trim();
        VBox page = new VBox(18);
        if (query.isEmpty()) page.getChildren().add(trendingPanel());

        List<Product> products = Dao.searchProducts(query);
        Map<String, Long> claimed = Dao.claimedTotals();

        VBox panel = Ui.panel();
        if (!query.isEmpty()) panel.getChildren().add(Ui.text("Results for “" + query + "”", "title-md"));
        if (products.isEmpty()) {
            panel.getChildren().add(Ui.text(query.isEmpty() ? "No products yet. Check back soon!"
                                                           : "Nothing found. Try another word.", "muted"));
        } else {
            panel.getChildren().add(grid(products, claimed));
        }
        page.getChildren().add(panel);
        return page;
    }

    private static Node trendingPanel() {
        VBox panel = Ui.panel(Ui.display("TRENDING", "display"));
        List<Product> trending = Dao.trending(8);
        if (trending.isEmpty()) {
            panel.getChildren().add(Ui.text("Nothing is trending yet. Products from the top-rated shops show up here once students leave reviews.", "muted"));
            return panel;
        }

        Map<Long, RatingInfo> ratings = Dao.ratingsByShop();
        HBox row = new HBox(22);
        for (Product p : trending) {
            RatingInfo r = ratings.getOrDefault(p.shopId(), RatingInfo.NONE);
            StackPane tile = new StackPane(Ui.photo(p.imageId(), Ui.initial(p.name()), 300, 230));
            Label name = Ui.text(p.name(), "tile-name");
            Label sub = Ui.text(p.shopName() + "  ·  ★ " + String.format("%.1f", r.average()) + "  ·  " + Ui.peso(p.price()), "tile-sub");
            VBox caption = new VBox(2, name, sub);
            caption.getStyleClass().add("tile-caption");
            caption.setMaxHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
            StackPane.setAlignment(caption, Pos.BOTTOM_CENTER);
            tile.getChildren().add(caption);
            tile.setMaxWidth(300);
            tile.setMaxHeight(230);
            tile.setOnMouseClicked(e -> App.product(p.id()));
            tile.setStyle("-fx-cursor: hand;");
            row.getChildren().add(tile);
        }

        ScrollPane scroll = new ScrollPane(row);
        scroll.setFitToHeight(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPannable(true);
        scroll.setPrefHeight(246);

        Button prev = new Button("◀");
        prev.getStyleClass().add("carousel-btn");
        prev.setPrefSize(38, 76);
        prev.setOnAction(e -> scroll.setHvalue(Math.max(0, scroll.getHvalue() - 0.4)));
        Button next = new Button("▶");
        next.getStyleClass().add("carousel-btn");
        next.setPrefSize(38, 76);
        next.setOnAction(e -> scroll.setHvalue(Math.min(1, scroll.getHvalue() + 0.4)));

        StackPane carousel = new StackPane(scroll, prev, next);
        StackPane.setAlignment(prev, Pos.CENTER_LEFT);
        StackPane.setAlignment(next, Pos.CENTER_RIGHT);
        panel.getChildren().add(carousel);
        return panel;
    }

    static FlowPane grid(List<Product> products, Map<String, Long> claimed) {
        FlowPane flow = new FlowPane(24, 26);
        for (Product p : products) flow.getChildren().add(card(p, claimed));
        return flow;
    }

    static Node card(Product p, Map<String, Long> claimed) {
        StackPane photo = Ui.photo(p.imageId(), Ui.initial(p.name()), 226, 198);
        if (!p.available()) {
            Label soldOut = Ui.text("SOLD OUT", "title-sm", "sold-out-overlay");
            soldOut.setStyle("-fx-text-fill: white;");
            soldOut.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            soldOut.setAlignment(Pos.CENTER);
            photo.getChildren().add(soldOut);
        }

        Label name = Ui.wrap(p.name(), "card-name");
        name.setMinHeight(56);
        name.setPrefWidth(226);
        name.setMaxWidth(226);
        Label shop = Ui.text(p.shopName(), "card-shop");
        shop.setMaxWidth(226);
        Label price = Ui.text(Ui.peso(p.price()), "card-meta");
        Label sold = Ui.text(Dao.sold(claimed, p) + " sold", "card-meta");

        VBox card = new VBox(6, photo, name, shop, Ui.hbox(0, price, Ui.spacer(), sold));
        card.getStyleClass().add("card");
        card.setMinWidth(250);
        card.setPrefWidth(250);
        card.setMaxWidth(250);
        card.setOnMouseClicked(e -> App.product(p.id()));
        return card;
    }

    // =====================================================================
    // About
    // =====================================================================

    static Node about() {
        return Ui.panel(
            Ui.display("WELCOME TO GRUB EAST", "display"),
            Ui.wrap("Grub East is a marketplace made by students, for students. It's one place to find student-run food "
                  + "shops and products, and the events our school organizations are putting on."),
            Ui.text("Why we built it", "title-md"),
            Ui.wrap("Student sellers usually post on the school freedom wall, but their posts get buried under everything "
                  + "else within hours. Grub East gives every shop its own page that stays put, with the products, prices, "
                  + "schedule and location in one spot, so buyers can actually find them."),
            Ui.text("Who it's for", "title-md"),
            Ui.wrap("Students: browse and search shops, read reviews and star ratings, add items to your cart and send "
                  + "preorders so your favorites are ready when you arrive."),
            Ui.wrap("Student businesses: set up your shop with your location, days, hours and product list. Turn on preorders "
                  + "and see exactly how much to prepare. New shops are reviewed by an admin before they go live."),
            Ui.wrap("Student organizations: post your upcoming events and find student businesses to sponsor them, "
                  + "or order food and products for your events."),
            Ui.wrap("Admins: approve new businesses and help with any problems or questions."));
    }

    // =====================================================================
    // Shop list
    // =====================================================================

    static Node shops(String q) {
        String query = q == null ? "" : q.trim();
        TextField search = Ui.field("Search shops or products", query);
        search.setOnAction(e -> App.shops(search.getText()));
        HBox bar = Ui.hbox(10, search, Ui.button("Search", () -> App.shops(search.getText())));
        HBox.setHgrow(search, Priority.ALWAYS);

        VBox page = new VBox(14, Ui.display("BROWSE SHOPS", "display"), bar);
        List<Shop> shops = Dao.visibleShops(query);
        Map<Long, RatingInfo> ratings = Dao.ratingsByShop();

        if (shops.isEmpty()) {
            page.getChildren().add(Ui.text(query.isEmpty() ? "No shops yet. Check back soon!" : "Nothing found for “" + query + "”.", "muted"));
        }
        for (Shop s : shops) {
            RatingInfo r = ratings.getOrDefault(s.id(), RatingInfo.NONE);
            VBox info = new VBox(4, Ui.text(s.ownerName(), "title-md"),
                ratingLine(r),
                Ui.text(s.allowPreorder() ? "Preorders open" : "Preorders closed", s.allowPreorder() ? "stars" : "muted"));
            if (s.description() != null) info.getChildren().add(Ui.wrap(s.description()));
            if (s.location() != null) info.getChildren().add(Ui.text("Location: " + s.location(), "muted"));
            HBox row = new HBox(18, Ui.photo(s.logoId(), Ui.initial(s.ownerName()), 90, 90), info);
            row.getStyleClass().add("panel");
            row.setOnMouseClicked(e -> App.shop(s.id()));
            row.setStyle("-fx-cursor: hand;");
            page.getChildren().add(row);
        }
        return page;
    }

    private static Node ratingLine(RatingInfo r) {
        if (r.count() == 0) return Ui.text("No reviews yet", "muted");
        return Ui.hbox(8, Ui.text(Ui.stars(r.average()), "stars"),
            Ui.text(String.format("%.1f", r.average()) + " (" + r.count() + (r.count() == 1 ? " review)" : " reviews)")));
    }

    // =====================================================================
    // One shop
    // =====================================================================

    static Node shop(long id) {
        Shop s = Dao.visibleShop(id);
        if (s == null) return Ui.panel(Ui.text("That shop isn't available.", "muted"));
        RatingInfo r = Dao.rating(id);

        VBox info = new VBox(6, Ui.text(s.ownerName(), "title-lg"), ratingLine(r),
            Ui.text(s.allowPreorder() ? "Preorders are open" : "Preorders are not available right now.",
                    s.allowPreorder() ? "stars" : "muted"));
        if (s.description() != null) info.getChildren().add(Ui.wrap(s.description()));
        info.getChildren().add(Ui.text("Location: " + orNotSet(s.location()), "muted"));
        info.getChildren().add(Ui.text("Days: " + orNotSet(s.availableDays()), "muted"));
        String hours = (s.openTime() != null && s.closeTime() != null) ? s.openTime() + " to " + s.closeTime() : "Not set yet";
        info.getChildren().add(Ui.text("Hours: " + hours, "muted"));
        if (Session.user != null && Session.user.id() != s.ownerId() && Dao.canMessage(Session.user, Dao.user(s.ownerId()))) {
            info.getChildren().add(Ui.outline("Message this shop", () -> App.chat(s.ownerId())));
        }
        HBox head = new HBox(24, Ui.photo(s.logoId(), Ui.initial(s.ownerName()), 150, 150), info);
        head.getStyleClass().add("panel");

        VBox page = new VBox(18, head);

        List<Product> products = Dao.productsOf(id);
        VBox productsPanel = Ui.panel(Ui.text("Products", "title-md"));
        if (products.isEmpty()) productsPanel.getChildren().add(Ui.text("This shop hasn't listed any products yet.", "muted"));
        else productsPanel.getChildren().add(grid(products, Dao.claimedTotals()));
        page.getChildren().add(productsPanel);

        VBox reviews = Ui.panel(Ui.text("Reviews", "title-md"));
        if (Session.isBuyer()) reviews.getChildren().add(reviewForm(s));
        else if (Session.user == null) reviews.getChildren().add(Ui.hbox(6, Ui.link("Log in", App::login), Ui.text("to leave a review.", "muted")));
        else reviews.getChildren().add(Ui.text("Only students and organizations can leave reviews.", "muted"));

        List<Review> list = Dao.reviews(id);
        if (list.isEmpty()) reviews.getChildren().add(Ui.text("No reviews yet. Be the first!", "muted"));
        for (Review rv : list) {
            VBox one = new VBox(2, Ui.hbox(10, Ui.text(Ui.stars(rv.rating()), "stars"), Ui.text(rv.authorName(), "title-sm"),
                                           Ui.text(rv.postedAt().format(Ui.DATE), "muted")));
            if (rv.body() != null) one.getChildren().add(Ui.wrap(rv.body()));
            one.getStyleClass().add("row-box");
            reviews.getChildren().add(one);
        }
        page.getChildren().add(reviews);
        return page;
    }

    private static String orNotSet(String s) {
        return s == null ? "Not set yet" : s;
    }

    private static Node reviewForm(Shop s) {
        Review mine = Dao.myReview(s.id(), Session.user.id());
        ToggleGroup group = new ToggleGroup();
        HBox stars = new HBox(14);
        for (int i = 1; i <= 5; i++) {
            RadioButton rb = new RadioButton(i + " ★");
            rb.setToggleGroup(group);
            rb.setUserData(i);
            if (mine != null && mine.rating() == i) rb.setSelected(true);
            stars.getChildren().add(rb);
        }
        TextArea body = Ui.area("Comment (optional)", mine == null ? "" : mine.body(), 3);

        VBox form = new VBox(8, Ui.text(mine == null ? "Leave a review" : "Edit your review", "title-sm"), stars, body);
        form.getChildren().add(Ui.button(mine == null ? "Post review" : "Save changes", () -> {
            Toggle t = group.getSelectedToggle();
            if (t == null) { Ui.error("Please choose a star rating."); return; }
            Dao.saveReview(s.id(), Session.user.id(), (Integer) t.getUserData(), body.getText());
            App.flash("Thanks! Your review was saved.", true);
            App.shop(s.id());
        }));
        if (mine != null) {
            form.getChildren().add(Ui.outline("Delete my review", () -> {
                Dao.deleteReview(s.id(), Session.user.id());
                App.shop(s.id());
            }));
        }
        form.getStyleClass().add("row-box");
        return form;
    }

    // =====================================================================
    // One product
    // =====================================================================

    static Node product(long id) {
        Product p = Dao.product(id);
        Shop shop = (p == null) ? null : Dao.visibleShop(p.shopId());
        if (p == null || shop == null) return Ui.panel(Ui.text("That product isn't available.", "muted"));

        RatingInfo rating = Dao.rating(shop.id());
        Map<String, Long> claimed = Dao.claimedTotals();

        VBox info = new VBox(10, Ui.wrap(p.name(), "title-lg"));
        if (rating.count() == 0) {
            info.getChildren().add(Ui.text("No reviews yet", "muted"));
        } else {
            info.getChildren().add(Ui.hbox(8, Ui.text(String.format("%.1f", rating.average())), Ui.text(Ui.stars(rating.average()), "stars"),
                Ui.link("(" + rating.count() + (rating.count() == 1 ? " shop review)" : " shop reviews)"), () -> App.shop(shop.id()))));
        }
        Label price = Ui.text(Ui.peso(p.price()), "price-banner");
        price.setMaxWidth(Double.MAX_VALUE);
        info.getChildren().addAll(price,
            Ui.wrap(p.description() == null ? "No description yet." : p.description()),
            Ui.text(Dao.sold(claimed, p) + " sold", "muted"));

        VBox spacer = new VBox();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        info.getChildren().add(spacer);

        if (Session.isBuyer() && shop.allowPreorder() && p.available()) {
            int[] qty = {1};
            Label qtyLabel = Ui.text("1", "qty-label");
            qtyLabel.setMinHeight(36);
            Button minus = new Button("−");
            minus.getStyleClass().add("step-btn");
            minus.setOnAction(e -> { qty[0] = Math.max(1, qty[0] - 1); qtyLabel.setText(String.valueOf(qty[0])); });
            Button plus = new Button("+");
            plus.getStyleClass().add("step-btn");
            plus.setOnAction(e -> { qty[0] = Math.min(50, qty[0] + 1); qtyLabel.setText(String.valueOf(qty[0])); });

            Button add = Ui.outline("Add to Cart", () -> {
                Dao.addToCart(Session.user.id(), p.id(), qty[0]);
                App.flash("Added to your cart.", true);
                App.product(p.id());
            });
            add.getStyleClass().add("btn-lg");
            add.setMaxWidth(Double.MAX_VALUE);
            Button buy = Ui.button("Buy now!", () -> {
                Dao.addToCart(Session.user.id(), p.id(), qty[0]);
                App.cart();
            });
            buy.getStyleClass().add("btn-lg");
            buy.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(add, Priority.ALWAYS);
            HBox.setHgrow(buy, Priority.ALWAYS);

            info.getChildren().add(Ui.hbox(12, Ui.text("QUANTITY", "title-md"), new HBox(minus, qtyLabel, plus)));
            info.getChildren().add(new HBox(18, add, buy));
        } else if (Session.user == null) {
            info.getChildren().add(Ui.hbox(6, Ui.link("Log in", App::login), Ui.text("to order this item.", "muted")));
        } else if (!Session.isBuyer()) {
            info.getChildren().add(Ui.text("Only students and organizations can place preorders.", "muted"));
        } else if (!p.available()) {
            info.getChildren().add(Ui.text("This item is sold out.", "muted"));
        } else {
            info.getChildren().add(Ui.text("This shop isn't taking preorders right now.", "muted"));
        }

        StackPane photo = Ui.photo(p.imageId(), Ui.initial(p.name()), 460, 425);
        HBox top = new HBox(30, photo, info);
        HBox.setHgrow(info, Priority.ALWAYS);
        top.getStyleClass().add("panel");

        // shop strip
        VBox strip = new VBox(6, Ui.text(shop.ownerName(), "title-md"),
            Ui.text(shop.allowPreorder() ? "Taking preorders" : "Preorders closed", "muted"));
        HBox buttons = new HBox(10);
        if (Session.user != null && Session.user.id() != shop.ownerId()
                && Dao.canMessage(Session.user, Dao.user(shop.ownerId()))) {
            buttons.getChildren().add(Ui.outline("Chat Now", () -> App.chat(shop.ownerId())));
        }
        buttons.getChildren().add(Ui.button("View Shop", () -> App.shop(shop.id())));
        strip.getChildren().add(buttons);
        HBox shopStrip = new HBox(20, Ui.photo(shop.logoId(), Ui.initial(shop.ownerName()), 110, 110), strip);
        shopStrip.getStyleClass().add("panel");

        VBox page = new VBox(18, top, shopStrip);

        List<Product> related = new ArrayList<>();
        for (Product o : Dao.productsOf(shop.id())) {
            if (o.id() != p.id() && related.size() < 8) related.add(o);
        }
        if (!related.isEmpty()) {
            page.getChildren().add(Ui.panel(Ui.text("More from this shop", "title-md"), grid(related, claimed)));
        }
        return page;
    }

    // =====================================================================
    // Cart
    // =====================================================================

    static Node cart() {
        if (!Session.isBuyer()) return Ui.panel(Ui.text("Log in as a student or organization to use the cart.", "muted"));
        long uid = Session.user.id();

        Map<Long, List<CartLine>> groups = new LinkedHashMap<>();
        for (CartLine l : Dao.cart(uid)) groups.computeIfAbsent(l.shopId(), k -> new ArrayList<>()).add(l);

        VBox page = new VBox(14, Ui.display("MY CART", "display"));
        if (groups.isEmpty()) {
            page.getChildren().add(Ui.panel(Ui.text("Your cart is empty. Browse the home page and look for shops with preorders open.", "muted"),
                Ui.button("Browse products", () -> App.home(""))));
            return page;
        }

        List<Event> myEvents = new ArrayList<>();
        if (Session.is(Models.ORG)) {
            for (Event e : Dao.eventsOf(uid)) {
                if (!e.date().isBefore(java.time.LocalDate.now())) myEvents.add(e);
            }
        }

        for (List<CartLine> lines : groups.values()) {
            CartLine first = lines.get(0);
            Button shopLink = Ui.link(first.shopName(), () -> App.shop(first.shopId()));
            shopLink.getStyleClass().add("title-md");
            VBox box = Ui.panel(shopLink);

            if (!first.shopAllowsPreorder()) {
                box.getChildren().add(Ui.text("This shop isn't taking preorders right now. Your items are saved here.", "flash-err"));
            }

            java.math.BigDecimal total = java.math.BigDecimal.ZERO;
            boolean anyAvailable = false;
            for (CartLine l : lines) {
                Spinner<Integer> spinner = new Spinner<>(0, 50, l.quantity());
                spinner.setPrefWidth(90);
                VBox name = new VBox(2, Ui.text(l.productName(), "title-sm"));
                if (!l.available()) name.getChildren().add(Ui.text("Sold out, skipped at checkout", "muted"));
                HBox row = new HBox(14,
                    Ui.photo(l.imageId(), Ui.initial(l.productName()), 56, 56, 12),
                    name, Ui.spacer(),
                    Ui.text(Ui.peso(l.price())), spinner,
                    Ui.small(Ui.outline("Update", () -> { Dao.setCartQty(l.id(), uid, spinner.getValue()); App.cart(); })),
                    Ui.text(Ui.peso(l.lineTotal()), "title-sm"),
                    Ui.small(Ui.button("Remove", () -> { Dao.removeFromCart(l.id(), uid); App.cart(); })));
                row.setAlignment(Pos.CENTER_LEFT);
                row.getStyleClass().add("row-box");
                box.getChildren().add(row);
                if (l.available()) { total = total.add(l.lineTotal()); anyAvailable = true; }
            }
            box.getChildren().add(Ui.text("Total: " + Ui.peso(total), "title-md"));

            if (first.shopAllowsPreorder() && anyAvailable) {
                ComboBox<String> eventBox = null;
                if (Session.is(Models.ORG)) {
                    eventBox = new ComboBox<>(FXCollections.observableArrayList());
                    eventBox.getItems().add("Not for a specific event");
                    for (Event e : myEvents) eventBox.getItems().add(e.title() + " (" + e.date().format(Ui.DATE) + ")");
                    eventBox.getSelectionModel().selectFirst();
                    eventBox.setMaxWidth(Double.MAX_VALUE);
                    box.getChildren().add(Ui.labeled("This order is for:", eventBox));
                    if (myEvents.isEmpty()) box.getChildren().add(Ui.text("You have no upcoming events. Post one in Manage my events to order for it.", "muted"));
                }
                TextField note = Ui.field("Note for the shop (optional)", "");
                box.getChildren().add(note);
                ComboBox<String> chosen = eventBox;
                box.getChildren().add(Ui.button("Place preorder with this shop", () -> {
                    Long eventId = null;
                    if (chosen != null && chosen.getSelectionModel().getSelectedIndex() > 0) {
                        eventId = myEvents.get(chosen.getSelectionModel().getSelectedIndex() - 1).id();
                    }
                    String error = Dao.checkout(uid, first.shopId(), eventId, note.getText());
                    if (error != null) {
                        App.flash(error, false);
                        App.cart();
                    } else {
                        App.flash("Preorder sent! The shop will confirm it soon.", true);
                        App.myOrders();
                    }
                }));
            }
            page.getChildren().add(box);
        }
        return page;
    }

    // =====================================================================
    // My preorders
    // =====================================================================

    static String statusText(String status) {
        return switch (status) {
            case Models.PENDING -> "Waiting for the shop to confirm";
            case Models.CONFIRMED -> "Confirmed";
            case Models.READY -> "Ready for pickup!";
            case Models.CLAIMED -> "Claimed";
            default -> "Cancelled";
        };
    }

    static Node myOrders() {
        if (Session.user == null) return Ui.panel(Ui.text("Log in to see your preorders.", "muted"));
        VBox page = new VBox(14, Ui.display("MY PREORDERS", "display"));
        List<Preorder> orders = Dao.preordersOfCustomer(Session.user.id());
        if (orders.isEmpty()) {
            page.getChildren().add(Ui.panel(Ui.text("You haven't placed any preorders yet. Open a product from a shop that has preorders open.", "muted")));
        }
        for (Preorder o : orders) {
            VBox box = Ui.panel(
                Ui.hbox(12, Ui.link(o.shopName(), () -> App.shop(o.shopId())), Ui.text(o.createdAt().format(Ui.DATE_TIME), "muted")),
                Ui.text("Status: " + statusText(o.status()), "title-sm"));
            if (o.eventTitle() != null) box.getChildren().add(Ui.text("For event: " + o.eventTitle() + " (" + o.eventDate().format(Ui.DATE) + ")"));
            for (Item i : o.items()) {
                box.getChildren().add(Ui.text(i.quantity() + " x " + i.name() + "   " + Ui.peso(i.lineTotal())));
            }
            box.getChildren().add(Ui.text("Total: " + Ui.peso(o.total()), "title-sm"));
            if (o.note() != null) box.getChildren().add(Ui.text("Your note: " + o.note(), "muted"));

            HBox actions = new HBox(10, Ui.outline("Message the shop", () -> {
                Shop s = Dao.visibleShop(o.shopId());
                if (s != null) App.chat(s.ownerId());
            }));
            if (o.status().equals(Models.PENDING)) {
                actions.getChildren().add(Ui.button("Cancel preorder", () -> {
                    if (Ui.confirm("Cancel this preorder?")) {
                        Dao.cancelOwnPreorder(o.id(), Session.user.id());
                        App.myOrders();
                    }
                }));
            }
            box.getChildren().add(actions);
            page.getChildren().add(box);
        }
        return page;
    }
}
