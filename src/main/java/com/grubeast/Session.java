package com.grubeast;

import com.grubeast.Models.User;

/** Who is logged in right now. */
public final class Session {
    private Session() {}

    public static User user;

    public static void refresh() {
        if (user != null) {
            User fresh = Dao.user(user.id());
            if (fresh != null) user = fresh;
        }
    }

    public static boolean is(String role) {
        return user != null && user.role().equals(role);
    }

    /** Students and organizations can order, review and use a cart. */
    public static boolean isBuyer() {
        return is(Models.USER) || is(Models.ORG);
    }

    public static boolean liveBusiness() {
        return is(Models.BUSINESS) && user.approved() && !user.rejected();
    }
}
