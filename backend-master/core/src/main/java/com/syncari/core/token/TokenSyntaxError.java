package com.syncari.core.token;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@Getter
@AllArgsConstructor
@ToString
public class TokenSyntaxError {
    private Object offendingSymbol;
    private int line;
    private int charPositionInLine;
    private String msg;
}
