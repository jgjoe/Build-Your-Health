package io.github.jgjoe.byh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import io.github.jgjoe.byh.support.IntegrationTestBase;
import io.github.jgjoe.byh.support.TestFixtures;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

/** Session handling at login (RQ-F-004): the session id changes on a successful login. */
class LoginSessionTest extends IntegrationTestBase {

    @Test
    void tc203_loginIssuesNewSessionId() throws Exception {
        insertMember("alice", "pw1234", "앨리스");
        MockHttpSession session = new MockHttpSession();
        String idBeforeLogin = session.getId();

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(TestFixtures.loginBody("alice", "pw1234").getBytes(StandardCharsets.UTF_8))
                        .session(session))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getRequest().getSession(false).getId())
                .as("a session id fixed before login must not survive it")
                .isNotEqualTo(idBeforeLogin);
    }
}
