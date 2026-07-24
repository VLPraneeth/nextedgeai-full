package com.syncari.connector.config;

import org.junit.Test;

import java.time.Instant;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AuthConfigTest {

    @Test
    public void testExpiry(){
        var auth =new AuthConfig()
                .setRefreshToken("SomeToken")
                .setLastRefreshed(Instant.now().minusSeconds(1*60))
                .setExpiresIn("661");
        assertFalse(auth.expiresSoon());
        auth.setLastRefreshed(Instant.now().minusSeconds(20*60));
        assertTrue(auth.expiresSoon());
        //token does not expire if refreshToken is not set
        auth.setRefreshToken(null);
        assertFalse(auth.expiresSoon());
        //token does expires if refreshToken is set but lastRefreshed is not
        auth.setRefreshToken("some").setLastRefreshed(null);
        assertTrue(auth.expiresSoon());
        //token does not expire if expiresIn is not set
        auth.setRefreshToken("some").setLastRefreshed(Instant.now().minusSeconds(25*60)).setExpiresIn(null);
        assertFalse(auth.expiresSoon());

        AuthConfig current = new AuthConfig();
        current.setRefreshToken("refreshToken");
        current.setAccessToken("oldAccessToken");
        current.setExpiresIn("1");
        current.setLastRefreshed(Instant.now().minusMillis(30000));
        assertTrue(current.expiresSoon());

        current = new AuthConfig();
        current.setRefreshToken("refreshToken");
        current.setAccessToken("oldAccessToken");
        current.setExpiresIn("540");
        current.setLastRefreshed(Instant.now().minusMillis(30000));
        assertFalse(current.expiresSoon());

        // Test Snowflake scenario: 600s token with 6 minutes elapsed should trigger refresh
        current = new AuthConfig();
        current.setRefreshToken("refreshToken");
        current.setAccessToken("oldAccessToken");
        current.setExpiresIn("600");  // Snowflake 10-minute token
        current.setLastRefreshed(Instant.now().minusSeconds(6*60 + 30));  // 6.5 minutes ago
        assertFalse(current.expiresSoon());  // Should refresh with 60 second buffer
        current.setLastRefreshed(Instant.now().minusSeconds(541));  // 6.5 minutes ago
        assertTrue(current.expiresSoon());  // Should refresh with 60 second buffer
    }

}
