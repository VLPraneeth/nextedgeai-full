package com.syncari.core.token;

import lombok.Getter;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

import java.util.ArrayList;
import java.util.List;

@Getter
public class TokenSyntaxErrorListener extends BaseErrorListener {
    private List<TokenSyntaxError> tokenSyntaxErrors = new ArrayList<>();

    @Override
    public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
        this.tokenSyntaxErrors.add(new TokenSyntaxError(offendingSymbol, line, charPositionInLine, msg));
        super.syntaxError(recognizer, offendingSymbol, line, charPositionInLine, msg, e);
    }
}
