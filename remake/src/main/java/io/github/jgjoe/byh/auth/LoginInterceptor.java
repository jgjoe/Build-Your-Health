package io.github.jgjoe.byh.auth;

import io.github.jgjoe.byh.common.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Single authentication check for {@code /api/orders/**} (RQ-F-005): every order endpoint
 * requires a logged-in member; the check is not repeated in the controllers.
 */
@Component
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (LoginMember.memberId(request.getSession(false)) == null) {
            throw new UnauthorizedException("로그인이 필요합니다.");
        }
        return true;
    }
}
