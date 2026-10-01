package io.github.jgjoe.byh.auth;

import jakarta.servlet.http.HttpSession;

/** Session handling of the logged-in member (RQ-F-004). */
public final class LoginMember {

    public static final String SESSION_ATTRIBUTE = "LOGIN_MEMBER_ID";

    private LoginMember() {
    }

    public static void login(HttpSession session, String memberId) {
        session.setAttribute(SESSION_ATTRIBUTE, memberId);
    }

    public static String memberId(HttpSession session) {
        if (session == null) {
            return null;
        }
        Object memberId = session.getAttribute(SESSION_ATTRIBUTE);
        return memberId == null ? null : String.valueOf(memberId);
    }
}
