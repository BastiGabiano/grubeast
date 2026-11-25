package com.grubeast;

import com.grubeast.Db.RowMapper;
import com.grubeast.Models.*;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Every question the app asks the database lives here. */
public final class Dao {
    private Dao() {}

    private static String clip(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        return t.length() > max ? t.substring(0, max) : t;
    }

    private static LocalDate date(ResultSet rs, String col) throws SQLException {
        java.sql.Date d = rs.getDate(col);
        return d == null ? null : d.toLocalDate();
    }

    // =====================================================================
    // Users
    // =====================================================================

    private static final RowMapper<User> USER_ROW = rs -> new User(
        rs.getLong("id"), rs.getString("name"), rs.getString("email"), rs.getString("role"),
        rs.getBoolean("approved"), rs.getBoolean("rejected"), rs.getString("rejection_reason"),
        rs.getString("bio"), rs.getString("contact"));

    public static User user(long id) {
        return Db.one("select * from users where id = ?", USER_ROW, id);
    }

    public static User firstAdmin() {
        return Db.one("select * from users where role = 'ADMIN' order by id limit 1", USER_ROW);
    }

    public static User login(String email, String password) {
        String e = email.trim().toLowerCase();
        String stored = Db.one("select password from users where email = ?", rs -> rs.getString(1), e);
        if (stored == null || !Passwords.matches(password, stored)) return null;
        return Db.one("select * from users where email = ?", USER_ROW, e);
    }

    /** Returns an error message, or null when the account was created. */
    public static String register(String name, String email, String password, String role) {
        String n = name.trim();
        String e = email.trim().toLowerCase();
        if (n.isEmpty()) return "Please enter a name.";
        if (!e.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) return "Please enter a valid email address.";
        if (password.length() < 6) return "Password must be at least 6 characters.";
        if (!(role.equals(Models.USER) || role.equals(Models.BUSINESS) || role.equals(Models.ORG))) {
            return "Please choose an account type.";
        }
        if (Db.one("select id from users where email = ?", rs -> rs.getLong(1), e) != null) {
            return "That email is already registered.";
        }
        Db.insert("insert into users (name, email, password, role, approved, rejected) values (?, ?, ?, ?, ?, 0)",
                  n, e, Passwords.hash(password), role, !role.equals(Models.BUSINESS));
        return null;
    }

    public static void updateProfile(long id, String name, String bio, String contact) {
        Db.update("update users set name = ?, bio = ?, contact = ? where id = ?",
                  name.trim(), clip(bio, 1000), clip(contact, 200), id);
    }

    public static boolean changePassword(long id, String oldPassword, String newPassword) {
        String stored = Db.one("select password from users where id = ?", rs -> rs.getString(1), id);
        if (stored == null || !Passwords.matches(oldPassword, stored)) return false;
        Db.update("update users set password = ? where id = ?", Passwords.hash(newPassword), id);
        return true;
    }

    public static List<User> businesses(boolean approved, boolean rejected) {
        return Db.query("select * from users where role = 'BUSINESS' and approved = ? and rejected = ? order by name",
                        USER_ROW, approved, rejected);
    }

    public static void approveBusiness(long id) {
        Db.update("update users set approved = 1, rejected = 0, rejection_reason = null where id = ? and role = 'BUSINESS'", id);
    }

    public static void rejectBusiness(long id, String reason) {
        Db.update("update users set rejected = 1, rejection_reason = ? where id = ? and role = 'BUSINESS' and approved = 0",
                  clip(reason, 300), id);
    }

    public static void reapply(long id) {
        Db.update("update users set rejected = 0, rejection_reason = null where id = ? and role = 'BUSINESS' and rejected = 1", id);
    }

    // =====================================================================
    // Photos (stored in MySQL so every installed copy can see them)
    // =====================================================================

    /** Resizes the picture to at most 900 px and stores it as a JPEG. Returns the new image id. */
    public static long saveImage(File file) throws IOException {
        if (file.length() > 25L * 1024 * 1024) throw new IOException("That photo is too big (25 MB limit).");
        BufferedImage src = ImageIO.read(file);
        if (src == null) throw new IOException("Please choose a JPG, PNG or GIF picture.");

        double scale = Math.min(1.0, 900.0 / Math.max(src.getWidth(), src.getHeight()));
        int w = Math.max(1, (int) Math.round(src.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(src.getHeight() * scale));

        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(out, "jpg", bytes);
        return Db.insert("insert into images (data) values (?)", (Object) bytes.toByteArray());
    }

    public static byte[] imageBytes(long id) {
        return Db.one("select data from images where id = ?", rs -> rs.getBytes(1), id);
    }

    private static void deleteImage(Long id) {
        if (id != null) Db.update("delete from images where id = ?", id);
    }

    // =====================================================================
    // Shops and products
    // =====================================================================

    private static final String SHOP_SQL =
        "select s.*, u.name as owner_name from shops s join users u on u.id = s.owner_id ";

    private static final RowMapper<Shop> SHOP_ROW = rs -> new Shop(
        rs.getLong("id"), rs.getLong("owner_id"), rs.getString("owner_name"), rs.getString("description"),
        rs.getString("location"), rs.getString("available_days"), rs.getString("open_time"),
        rs.getString("close_time"), rs.getBoolean("allow_preorder"), Db.nullableLong(rs, "logo_id"));

    private static final String PRODUCT_SQL =
        "select p.*, u.name as shop_name from products p join shops s on s.id = p.shop_id "
      + "join users u on u.id = s.owner_id ";

    private static final RowMapper<Product> PRODUCT_ROW = rs -> new Product(
        rs.getLong("id"), rs.getLong("shop_id"), rs.getString("shop_name"), rs.getString("name"),
        rs.getBigDecimal("price"), rs.getBoolean("available"), rs.getString("description"),
        Db.nullableLong(rs, "image_id"));

    /** The business's own shop (created the first time it is needed). */
    public static Shop shopOf(long ownerId) {
        Shop s = Db.one(SHOP_SQL + "where s.owner_id = ?", SHOP_ROW, ownerId);
        if (s == null) {
            Db.insert("insert into shops (owner_id) values (?)", ownerId);
            s = Db.one(SHOP_SQL + "where s.owner_id = ?", SHOP_ROW, ownerId);
        }
        return s;
    }

    /** A shop that customers are allowed to see (null if hidden or missing). */
    public static Shop visibleShop(long id) {
        return Db.one(SHOP_SQL + "where s.id = ? and u.approved = 1 and u.rejected = 0", SHOP_ROW, id);
    }

    public static List<Shop> visibleShops(String q) {
        String text = q.toLowerCase();
        String like = "%" + text + "%";
        return Db.query(SHOP_SQL
            + "where u.approved = 1 and u.rejected = 0 and u.role = 'BUSINESS' and (? = '' "
            + "or lower(u.name) like ? or lower(coalesce(s.description, '')) like ? "
            + "or exists (select 1 from products p where p.shop_id = s.id and lower(p.name) like ?)) "
            + "order by u.name", SHOP_ROW, text, like, like, like);
    }

    public static void saveShopDetails(long shopId, String description, String location, String days,
                                       String open, String close) {
        Db.update("update shops set description = ?, location = ?, available_days = ?, open_time = ?, close_time = ? where id = ?",
                  clip(description, 1000), clip(location, 200), clip(days, 200), clip(open, 10), clip(close, 10), shopId);
    }

    public static void setPreorder(long shopId, boolean on) {
        Db.update("update shops set allow_preorder = ? where id = ?", on, shopId);
    }

    public static void setLogo(long shopId, long imageId) {
        Long old = Db.one("select logo_id from shops where id = ?", rs -> Db.nullableLong(rs, "logo_id"), shopId);
        Db.update("update shops set logo_id = ? where id = ?", imageId, shopId);
        deleteImage(old);
    }

    public static List<Product> productsOf(long shopId) {
        return Db.query(PRODUCT_SQL + "where p.shop_id = ? order by p.id", PRODUCT_ROW, shopId);
    }

    public static Product product(long id) {
        return Db.one(PRODUCT_SQL + "where p.id = ?", PRODUCT_ROW, id);
    }

    public static List<Product> searchProducts(String q) {
        String text = q.toLowerCase();
        String like = "%" + text + "%";
        return Db.query(PRODUCT_SQL
            + "where u.approved = 1 and u.rejected = 0 and (? = '' or lower(p.name) like ? "
            + "or lower(u.name) like ? or lower(coalesce(p.description, '')) like ?) "
            + "order by p.available desc, p.id desc", PRODUCT_ROW, text, like, like, like);
    }

    public static void addProduct(long shopId, String name, BigDecimal price, String description, Long imageId) {
        Db.insert("insert into products (shop_id, name, price, available, description, image_id) values (?, ?, ?, 1, ?, ?)",
                  shopId, name.trim(), price, clip(description, 500), imageId);
    }

    public static void toggleProduct(long productId, long ownerId) {
        Db.update("update products set available = 1 - available where id = ? "
                + "and shop_id = (select id from shops where owner_id = ?)", productId, ownerId);
    }

    public static void setProductImage(long productId, long ownerId, long imageId) {
        Long old = Db.one("select image_id from products where id = ? and shop_id = (select id from shops where owner_id = ?)",
                          rs -> Db.nullableLong(rs, "image_id"), productId, ownerId);
        Db.update("update products set image_id = ? where id = ? and shop_id = (select id from shops where owner_id = ?)",
                  imageId, productId, ownerId);
        deleteImage(old);
    }

    public static void deleteProduct(long productId, long ownerId) {
        Long old = Db.one("select image_id from products where id = ? and shop_id = (select id from shops where owner_id = ?)",
                          rs -> Db.nullableLong(rs, "image_id"), productId, ownerId);
        Db.update("delete from products where id = ? and shop_id = (select id from shops where owner_id = ?)",
                  productId, ownerId);
        deleteImage(old);
    }

    // =====================================================================
    // Ratings, trending and "sold" counts
    // =====================================================================

    public static RatingInfo rating(long shopId) {
        RatingInfo r = Db.one("select avg(rating) as a, count(*) as c from reviews where shop_id = ?",
                              rs -> new RatingInfo(rs.getDouble("a"), rs.getLong("c")), shopId);
        return r == null ? RatingInfo.NONE : r;
    }

    public static Map<Long, RatingInfo> ratingsByShop() {
        Map<Long, RatingInfo> map = new HashMap<>();
        Db.query("select shop_id, avg(rating) as a, count(*) as c from reviews group by shop_id", rs -> {
            map.put(rs.getLong("shop_id"), new RatingInfo(rs.getDouble("a"), rs.getLong("c")));
            return null;
        });
        return map;
    }

    /** Available products from the best-rated shops (only shops with reviews), up to 3 per shop. */
    public static List<Product> trending(int limit) {
        Map<Long, RatingInfo> ratings = ratingsByShop();
        List<Shop> shops = new ArrayList<>(visibleShops(""));
        shops.removeIf(s -> !ratings.containsKey(s.id()));
        shops.sort(Comparator.comparingDouble((Shop s) -> ratings.get(s.id()).average()).reversed()
            .thenComparing((Shop s) -> ratings.get(s.id()).count(), Comparator.<Long>reverseOrder()));

        List<Product> result = new ArrayList<>();
        for (Shop s : shops) {
            int taken = 0;
            for (Product p : productsOf(s.id())) {
                if (!p.available()) continue;
                result.add(p);
                taken++;
                if (taken == 3 || result.size() >= limit) break;
            }
            if (result.size() >= limit) break;
        }
        return result;
    }

    /** shopId|product name  ->  units customers have already claimed. */
    public static Map<String, Long> claimedTotals() {
        Map<String, Long> map = new HashMap<>();
        Db.query("select p.shop_id as sid, i.product_name as pname, sum(i.quantity) as total "
               + "from preorder_items i join preorders p on p.id = i.preorder_id "
               + "where p.status = 'CLAIMED' group by p.shop_id, i.product_name", rs -> {
            map.put(rs.getLong("sid") + "|" + rs.getString("pname").toLowerCase(), rs.getLong("total"));
            return null;
        });
        return map;
    }

    public static long sold(Map<String, Long> claimed, Product p) {
        return claimed.getOrDefault(p.shopId() + "|" + p.name().toLowerCase(), 0L);
    }

    // =====================================================================
    // Reviews
    // =====================================================================

    private static final RowMapper<Review> REVIEW_ROW = rs -> new Review(
        rs.getLong("id"), rs.getLong("shop_id"), rs.getLong("author_id"), rs.getString("author_name"),
        rs.getInt("rating"), rs.getString("body"), Db.time(rs, "posted_at"));

    private static final String REVIEW_SQL =
        "select r.*, u.name as author_name from reviews r join users u on u.id = r.author_id ";

    public static List<Review> reviews(long shopId) {
        return Db.query(REVIEW_SQL + "where r.shop_id = ? order by r.posted_at desc", REVIEW_ROW, shopId);
    }

    public static Review myReview(long shopId, long userId) {
        return Db.one(REVIEW_SQL + "where r.shop_id = ? and r.author_id = ?", REVIEW_ROW, shopId, userId);
    }

    public static void saveReview(long shopId, long userId, int rating, String body) {
        Db.update("insert into reviews (shop_id, author_id, rating, body, posted_at) values (?, ?, ?, ?, ?) "
                + "on duplicate key update rating = values(rating), body = values(body), posted_at = values(posted_at)",
                  shopId, userId, rating, clip(body, 500), LocalDateTime.now());
    }

    public static void deleteReview(long shopId, long userId) {
        Db.update("delete from reviews where shop_id = ? and author_id = ?", shopId, userId);
    }

    // =====================================================================
    // Cart and preorders
    // =====================================================================

    private static final RowMapper<CartLine> CART_ROW = rs -> new CartLine(
        rs.getLong("id"), rs.getLong("pid"), rs.getLong("sid"), rs.getString("shop_name"),
        rs.getString("name"), rs.getBigDecimal("price"), rs.getBoolean("available"),
        rs.getBoolean("allow_preorder"), Db.nullableLong(rs, "image_id"), rs.getInt("quantity"));

    public static List<CartLine> cart(long userId) {
        return Db.query("select c.id, c.quantity, p.id as pid, p.name, p.price, p.available, p.image_id, "
            + "s.id as sid, s.allow_preorder, u.name as shop_name "
            + "from cart_items c join products p on p.id = c.product_id join shops s on s.id = p.shop_id "
            + "join users u on u.id = s.owner_id where c.user_id = ? order by c.id", CART_ROW, userId);
    }

    public static int cartCount(long userId) {
        Long n = Db.one("select coalesce(sum(quantity), 0) from cart_items where user_id = ?", rs -> rs.getLong(1), userId);
        return n == null ? 0 : n.intValue();
    }

    public static void addToCart(long userId, long productId, int qty) {
        Db.update("insert into cart_items (user_id, product_id, quantity) values (?, ?, ?) "
                + "on duplicate key update quantity = least(50, quantity + values(quantity))", userId, productId, qty);
    }

    public static void setCartQty(long cartId, long userId, int qty) {
        if (qty <= 0) removeFromCart(cartId, userId);
        else Db.update("update cart_items set quantity = ? where id = ? and user_id = ?", Math.min(qty, 50), cartId, userId);
    }

    public static void removeFromCart(long cartId, long userId) {
        Db.update("delete from cart_items where id = ? and user_id = ?", cartId, userId);
    }

    /** Turns one shop's cart items into a preorder. Returns an error message, or null on success. */
    public static String checkout(long userId, long shopId, Long eventId, String note) {
        List<CartLine> lines = new ArrayList<>();
        for (CartLine l : cart(userId)) if (l.shopId() == shopId) lines.add(l);
        if (lines.isEmpty()) return "Your cart has nothing from that shop.";
        if (!lines.get(0).shopAllowsPreorder()) return "That shop isn't taking preorders right now.";

        List<CartLine> available = new ArrayList<>();
        for (CartLine l : lines) if (l.available()) available.add(l);
        if (available.isEmpty()) return "Everything from that shop is sold out.";

        Long event = null;
        if (eventId != null) {
            Long ok = Db.one("select id from events where id = ? and organizer_id = ? and event_date >= ?",
                             rs -> rs.getLong(1), eventId, userId, LocalDate.now());
            if (ok != null) event = eventId;
        }

        Db.begin();
        try {
            long preorderId = Db.insert(
                "insert into preorders (shop_id, customer_id, event_id, status, note, created_at) values (?, ?, ?, ?, ?, ?)",
                shopId, userId, event, Models.PENDING, clip(note, 300), LocalDateTime.now());
            for (CartLine l : available) {
                Db.insert("insert into preorder_items (preorder_id, product_name, unit_price, quantity) values (?, ?, ?, ?)",
                          preorderId, l.productName(), l.price(), l.quantity());
            }
            Db.update("delete from cart_items where user_id = ? and product_id in (select id from products where shop_id = ?)",
                      userId, shopId);
            Db.commit();
        } catch (RuntimeException e) {
            Db.rollback();
            throw e;
        }
        return null;
    }

    private static final String PREORDER_SQL =
        "select pr.*, so.name as shop_name, cu.name as customer_name, e.title as event_title, e.event_date as event_date "
      + "from preorders pr join shops s on s.id = pr.shop_id join users so on so.id = s.owner_id "
      + "join users cu on cu.id = pr.customer_id left join events e on e.id = pr.event_id ";

    private static List<Preorder> loadPreorders(String where, Object... args) {
        List<Preorder> list = Db.query(PREORDER_SQL + where + " order by pr.created_at desc, pr.id desc",
            rs -> new Preorder(rs.getLong("id"), rs.getLong("shop_id"), rs.getString("shop_name"),
                rs.getLong("customer_id"), rs.getString("customer_name"), Db.nullableLong(rs, "event_id"),
                rs.getString("event_title"), date(rs, "event_date"), rs.getString("status"),
                rs.getString("note"), Db.time(rs, "created_at"), new ArrayList<Item>()), args);
        for (Preorder p : list) {
            p.items().addAll(Db.query("select * from preorder_items where preorder_id = ? order by id",
                rs -> new Item(rs.getString("product_name"), rs.getBigDecimal("unit_price"), rs.getInt("quantity")), p.id()));
        }
        return list;
    }

    public static List<Preorder> preordersOfCustomer(long userId) { return loadPreorders("where pr.customer_id = ?", userId); }
    public static List<Preorder> preordersOfShop(long shopId) { return loadPreorders("where pr.shop_id = ?", shopId); }
    public static List<Preorder> preordersOfEvent(long eventId) { return loadPreorders("where pr.event_id = ?", eventId); }

    public static void cancelOwnPreorder(long preorderId, long userId) {
        Db.update("update preorders set status = 'CANCELLED' where id = ? and customer_id = ? and status = 'PENDING'",
                  preorderId, userId);
    }

    /** action: confirm, ready, claim or cancel. Only works on the business's own shop. */
    public static void shopAction(long preorderId, long ownerId, String action) {
        String set;
        String from;
        switch (action) {
            case "confirm" -> { set = Models.CONFIRMED; from = "'PENDING'"; }
            case "ready" -> { set = Models.READY; from = "'CONFIRMED'"; }
            case "claim" -> { set = Models.CLAIMED; from = "'CONFIRMED', 'READY'"; }
            case "cancel" -> { set = Models.CANCELLED; from = "'PENDING', 'CONFIRMED'"; }
            default -> { return; }
        }
        Db.update("update preorders set status = ? where id = ? and status in (" + from + ") "
                + "and shop_id = (select id from shops where owner_id = ?)", set, preorderId, ownerId);
    }

    /** What the business still has to make: pending + confirmed orders, per product. */
    public static Map<String, Long> toPrepare(long shopId) {
        Map<String, Long> map = new LinkedHashMap<>();
        Db.query("select i.product_name as pname, sum(i.quantity) as total from preorder_items i "
               + "join preorders p on p.id = i.preorder_id where p.shop_id = ? and p.status in ('PENDING', 'CONFIRMED') "
               + "group by i.product_name order by i.product_name", rs -> {
            map.put(rs.getString("pname"), rs.getLong("total"));
            return null;
        }, shopId);
        return map;
    }

    /** Same totals, but only orders placed by organizations for an event, grouped by event. */
    public static Map<String, Map<String, Long>> toPrepareByEvent(long shopId) {
        Map<String, Map<String, Long>> map = new LinkedHashMap<>();
        Db.query("select e.id as eid, e.title as title, e.event_date as edate, o.name as org, "
               + "i.product_name as pname, sum(i.quantity) as total "
               + "from preorder_items i join preorders p on p.id = i.preorder_id "
               + "join events e on e.id = p.event_id join users o on o.id = e.organizer_id "
               + "where p.shop_id = ? and p.status in ('PENDING', 'CONFIRMED') "
               + "group by e.id, e.title, e.event_date, o.name, i.product_name order by e.event_date, i.product_name", rs -> {
            String key = rs.getString("title") + " (" + rs.getString("org") + ", " + date(rs, "edate") + ")";
            map.computeIfAbsent(key, k -> new LinkedHashMap<>()).put(rs.getString("pname"), rs.getLong("total"));
            return null;
        }, shopId);
        return map;
    }

    /** Everything an organization has ordered for one event (cancelled orders not counted). */
    public static Map<String, Long> eventOrderTotals(long eventId) {
        Map<String, Long> map = new LinkedHashMap<>();
        Db.query("select i.product_name as pname, sum(i.quantity) as total from preorder_items i "
               + "join preorders p on p.id = i.preorder_id where p.event_id = ? and p.status <> 'CANCELLED' "
               + "group by i.product_name order by i.product_name", rs -> {
            map.put(rs.getString("pname"), rs.getLong("total"));
            return null;
        }, eventId);
        return map;
    }

    // =====================================================================
    // Events and sponsors
    // =====================================================================

    private static final String EVENT_SQL =
        "select e.*, u.name as organizer_name, u.contact as organizer_contact "
      + "from events e join users u on u.id = e.organizer_id ";

    private static final RowMapper<Event> EVENT_ROW = rs -> new Event(
        rs.getLong("id"), rs.getLong("organizer_id"), rs.getString("organizer_name"),
        rs.getString("organizer_contact"), rs.getString("title"), rs.getString("description"),
        date(rs, "event_date"), rs.getString("start_time"), rs.getString("end_time"),
        rs.getString("location"), rs.getBoolean("seeking_sponsors"), Db.nullableLong(rs, "image_id"));

    public static List<Event> upcomingEvents() {
        return Db.query(EVENT_SQL + "where e.event_date >= ? order by e.event_date, e.id", EVENT_ROW, LocalDate.now());
    }

    public static List<Event> eventsOf(long organizerId) {
        return Db.query(EVENT_SQL + "where e.organizer_id = ? order by e.event_date, e.id", EVENT_ROW, organizerId);
    }

    public static Event event(long id) {
        return Db.one(EVENT_SQL + "where e.id = ?", EVENT_ROW, id);
    }

    /** Creates the event when id is null, otherwise edits it (only if it belongs to this organizer). Returns the event id. */
    public static long saveEvent(Long id, long organizerId, String title, String description, LocalDate date,
                                 String start, String end, String location, boolean seeking, Long newImageId) {
        if (id == null) {
            return Db.insert("insert into events (organizer_id, title, description, event_date, start_time, end_time, "
                + "location, seeking_sponsors, image_id) values (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                organizerId, title.trim(), clip(description, 1000), date, clip(start, 10), clip(end, 10),
                clip(location, 200), seeking, newImageId);
        }
        Db.update("update events set title = ?, description = ?, event_date = ?, start_time = ?, end_time = ?, "
            + "location = ?, seeking_sponsors = ? where id = ? and organizer_id = ?",
            title.trim(), clip(description, 1000), date, clip(start, 10), clip(end, 10), clip(location, 200),
            seeking, id, organizerId);
        if (newImageId != null) {
            Long old = Db.one("select image_id from events where id = ? and organizer_id = ?",
                              rs -> Db.nullableLong(rs, "image_id"), id, organizerId);
            Db.update("update events set image_id = ? where id = ? and organizer_id = ?", newImageId, id, organizerId);
            deleteImage(old);
        }
        return id;
    }

    public static void deleteEvent(long id, long organizerId) {
        Long old = Db.one("select image_id from events where id = ? and organizer_id = ?",
                          rs -> Db.nullableLong(rs, "image_id"), id, organizerId);
        Db.update("delete from events where id = ? and organizer_id = ?", id, organizerId);
        deleteImage(old);
    }

    private static final String SPONSOR_SQL =
        "select a.*, e.title as event_title, e.event_date as event_date, b.name as business_name, b.email as business_email "
      + "from sponsor_applications a join events e on e.id = a.event_id join users b on b.id = a.business_id ";

    private static final RowMapper<Sponsor> SPONSOR_ROW = rs -> new Sponsor(
        rs.getLong("id"), rs.getLong("event_id"), rs.getString("event_title"), date(rs, "event_date"),
        rs.getLong("business_id"), rs.getString("business_name"), rs.getString("business_email"),
        rs.getString("message"), rs.getString("status"));

    public static List<Sponsor> applicationsForEvent(long eventId) {
        return Db.query(SPONSOR_SQL + "where a.event_id = ? order by a.id", SPONSOR_ROW, eventId);
    }

    public static List<Sponsor> acceptedSponsors(long eventId) {
        return Db.query(SPONSOR_SQL + "where a.event_id = ? and a.status = 'ACCEPTED' order by a.id", SPONSOR_ROW, eventId);
    }

    public static List<Sponsor> applicationsOfBusiness(long businessId) {
        return Db.query(SPONSOR_SQL + "where a.business_id = ? order by e.event_date, a.id", SPONSOR_ROW, businessId);
    }

    public static Sponsor myApplication(long eventId, long businessId) {
        return Db.one(SPONSOR_SQL + "where a.event_id = ? and a.business_id = ?", SPONSOR_ROW, eventId, businessId);
    }

    /** Returns an error message, or null when the application was sent. */
    public static String applyToSponsor(long eventId, long businessId, String message) {
        Event e = event(eventId);
        if (e == null || !e.seekingSponsors() || e.date().isBefore(LocalDate.now())) {
            return "That event isn't looking for sponsors.";
        }
        if (myApplication(eventId, businessId) != null) return "You already applied to this event.";
        Db.insert("insert into sponsor_applications (event_id, business_id, message, status) values (?, ?, ?, 'PENDING')",
                  eventId, businessId, clip(message, 500));
        return null;
    }

    public static void respondToSponsor(long applicationId, long organizerId, boolean accept) {
        Db.update("update sponsor_applications set status = ? where id = ? "
                + "and event_id in (select id from events where organizer_id = ?)",
                  accept ? "ACCEPTED" : "DECLINED", applicationId, organizerId);
    }

    // =====================================================================
    // Chat
    // =====================================================================

    private static boolean live(User u) {
        return !u.role().equals(Models.BUSINESS) || (u.approved() && !u.rejected());
    }

    public static boolean canMessage(User a, User b) {
        if (a.id() == b.id()) return false;
        if (a.role().equals(Models.USER) && b.role().equals(Models.USER)) return false;
        if (a.role().equals(Models.ADMIN) || b.role().equals(Models.ADMIN)) return true;
        return live(a) && live(b);
    }

    private static final RowMapper<Message> MESSAGE_ROW = rs -> new Message(
        rs.getLong("id"), rs.getLong("sender_id"), rs.getLong("recipient_id"), rs.getString("body"),
        Db.time(rs, "sent_at"), rs.getBoolean("seen"));

    public static List<Message> conversation(long a, long b) {
        return Db.query("select * from messages where (sender_id = ? and recipient_id = ?) "
            + "or (sender_id = ? and recipient_id = ?) order by sent_at, id", MESSAGE_ROW, a, b, b, a);
    }

    public static void sendMessage(long from, long to, String body) {
        String text = clip(body, 1000);
        if (text == null) return;
        Db.insert("insert into messages (sender_id, recipient_id, body, sent_at, seen) values (?, ?, ?, ?, 0)",
                  from, to, text, LocalDateTime.now());
    }

    public static void markSeen(long partnerId, long myId) {
        Db.update("update messages set seen = 1 where sender_id = ? and recipient_id = ? and seen = 0", partnerId, myId);
    }

    public static int unread(long userId) {
        Long n = Db.one("select count(*) from messages where recipient_id = ? and seen = 0", rs -> rs.getLong(1), userId);
        return n == null ? 0 : n.intValue();
    }

    public static List<Conversation> inbox(long userId) {
        Map<Long, Message> last = new LinkedHashMap<>();
        Map<Long, Integer> unread = new HashMap<>();
        for (Message m : Db.query("select * from messages where sender_id = ? or recipient_id = ? "
                                + "order by sent_at desc, id desc", MESSAGE_ROW, userId, userId)) {
            long partner = m.senderId() == userId ? m.recipientId() : m.senderId();
            last.putIfAbsent(partner, m);
            if (m.recipientId() == userId && !m.seen()) unread.merge(partner, 1, Integer::sum);
        }
        List<Conversation> list = new ArrayList<>();
        for (Map.Entry<Long, Message> e : last.entrySet()) {
            User partner = user(e.getKey());
            if (partner != null) list.add(new Conversation(partner, e.getValue(), unread.getOrDefault(e.getKey(), 0)));
        }
        return list;
    }
}
