package com.syncari.core.model.misc;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Duration;
import java.time.Instant;
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class UserLoginDetails {

    private String tokenId;
    private Instant lastAccessed = Instant.now();
    // token to be valid for in milliseconds
    private Long tokenValidFor;

    public UserLoginDetails(String tokenId, Long tokeValidFor) {
        this.tokenId = tokenId;
        this.tokenValidFor = tokeValidFor;
    }

    public boolean isValidLogin(){
        return Instant.now().minusMillis(lastAccessed.toEpochMilli()).toEpochMilli() <= tokenValidFor;
    }

    public String toString() {
        return "UserLoginDetails{" + "lastAccessed="+lastAccessed + ", tokenValidFor=" + tokenValidFor + "}";
    }

}
