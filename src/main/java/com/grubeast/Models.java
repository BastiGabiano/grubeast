package com.grubeast;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Plain data holders for the rows we read from MySQL. */
public final class Models {
    private Models() {}

    // account types
    public static final String USER = "USER";
    public static final String BUSINESS = "BUSINESS";
    public static final String ORG = "ORG";
    public static final String ADMIN = "ADMIN";

    // preorder statuses
    public static final String PENDING = "PENDING";
    public static final String CONFIRMED = "CONFIRMED";
    public static final String READY = "READY";
    public static final String CLAIMED = "CLAIMED";
    public static final String CANCELLED = "CANCELLED";

    public record User(long id, String name, String email, String role, boolean approved,
                       boolean rejected, String rejectionReason, String bio, String contact) {
        public String roleLabel() {
            return switch (role) {
                case USER -> "Student";
                case BUSINESS -> "Student business";
                case ORG -> "Student organization";
                default -> "Admin";
            };
        }
    }

    public record Shop(long id, long ownerId, String ownerName, String description, String location,
                       String availableDays, String openTime, String closeTime,
                       boolean allowPreorder, Long logoId) {}

    public record Product(long id, long shopId, String shopName, String name, BigDecimal price,
                          boolean available, String description, Long imageId) {}

    public record Review(long id, long shopId, long authorId, String authorName, int rating,
                         String body, LocalDateTime postedAt) {}

    public record Event(long id, long organizerId, String organizerName, String organizerContact,
                        String title, String description, LocalDate date, String startTime,
                        String endTime, String location, boolean seekingSponsors, Long imageId) {}

    public record Sponsor(long id, long eventId, String eventTitle, LocalDate eventDate,
                          long businessId, String businessName, String businessEmail,
                          String message, String status) {}

    public record Message(long id, long senderId, long recipientId, String body,
                          LocalDateTime sentAt, boolean seen) {}

    public record Conversation(User partner, Message last, int unread) {}

    public record CartLine(long id, long productId, long shopId, String shopName, String productName,
                           BigDecimal price, boolean available, boolean shopAllowsPreorder,
                           Long imageId, int quantity) {
        public BigDecimal lineTotal() {
            return price.multiply(BigDecimal.valueOf(quantity));
        }
    }

    public record Item(String name, BigDecimal price, int quantity) {
        public BigDecimal lineTotal() {
            return price.multiply(BigDecimal.valueOf(quantity));
        }
    }

    public record Preorder(long id, long shopId, String shopName, long customerId, String customerName,
                           Long eventId, String eventTitle, LocalDate eventDate, String status,
                           String note, LocalDateTime createdAt, List<Item> items) {
        public BigDecimal total() {
            BigDecimal sum = BigDecimal.ZERO;
            for (Item i : items) sum = sum.add(i.lineTotal());
            return sum;
        }
    }

    public record RatingInfo(double average, long count) {
        public static final RatingInfo NONE = new RatingInfo(0, 0);
    }
}
