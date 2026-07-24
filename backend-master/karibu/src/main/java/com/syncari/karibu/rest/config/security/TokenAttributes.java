package com.syncari.karibu.rest.config.security;

import lombok.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

@Data
@AllArgsConstructor
public class TokenAttributes {

    private String username;
    private String syncariId;
    private String tokenId;
    private Boolean ghosted;
    private List<SimpleGrantedAuthority> authorities;

}
