package com.studen.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthRateLimitFilterTest {

    @Test
    void requestsWithinLimit_areAllowedThrough() throws Exception {
        AuthRateLimitFilter filter = new AuthRateLimitFilter(3, 60);

        for (int i = 0; i < 3; i++) {
            MockFilterChain chain = new MockFilterChain();
            filter.doFilter(loginRequest("10.0.0.1"), new MockHttpServletResponse(), chain);
            assertThat(chain.getRequest()).isNotNull();
        }
    }

    @Test
    void exceedingLimit_returns429AndStopsTheChain() throws Exception {
        AuthRateLimitFilter filter = new AuthRateLimitFilter(3, 60);

        for (int i = 0; i < 3; i++) {
            filter.doFilter(loginRequest("10.0.0.2"), new MockHttpServletResponse(), new MockFilterChain());
        }

        MockFilterChain blockedChain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(loginRequest("10.0.0.2"), response, blockedChain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(blockedChain.getRequest()).isNull();
    }

    @Test
    void limitIsTrackedPerIp_independently() throws Exception {
        AuthRateLimitFilter filter = new AuthRateLimitFilter(1, 60);

        filter.doFilter(loginRequest("10.0.0.3"), new MockHttpServletResponse(), new MockFilterChain());

        // A different IP must not be affected by 10.0.0.3 already being at its limit.
        MockFilterChain otherIpChain = new MockFilterChain();
        filter.doFilter(loginRequest("10.0.0.4"), new MockHttpServletResponse(), otherIpChain);

        assertThat(otherIpChain.getRequest()).isNotNull();
    }

    @Test
    void nonAuthEndpoints_areNeverRateLimited() throws Exception {
        AuthRateLimitFilter filter = new AuthRateLimitFilter(1, 60);
        MockHttpServletRequest unrelated = new MockHttpServletRequest("GET", "/api/v1/portfolio/me");

        for (int i = 0; i < 10; i++) {
            MockFilterChain chain = new MockFilterChain();
            filter.doFilter(unrelated, new MockHttpServletResponse(), chain);
            assertThat(chain.getRequest()).isNotNull();
        }
    }

    private MockHttpServletRequest loginRequest(String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr(remoteAddr);
        return request;
    }
}
