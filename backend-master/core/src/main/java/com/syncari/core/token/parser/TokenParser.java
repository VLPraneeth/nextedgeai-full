// Generated from Token.g4 by ANTLR 4.12.0

package com.syncari.core.token.parser;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.ATN;
import org.antlr.v4.runtime.atn.ATNDeserializer;
import org.antlr.v4.runtime.atn.ParserATNSimulator;
import org.antlr.v4.runtime.atn.PredictionContextCache;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.tree.ParseTreeVisitor;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.List;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast", "CheckReturnValue"})
public class TokenParser extends Parser {
    static {
        RuntimeMetaData.checkVersion("4.12.0", RuntimeMetaData.VERSION);
    }

    protected static final DFA[] _decisionToDFA;
    protected static final PredictionContextCache _sharedContextCache =
            new PredictionContextCache();
    public static final int
            T__0 = 1, T__1 = 2, T__2 = 3, INT = 4, TXT = 5, NEWLINE = 6;
    public static final int
            RULE_tok_string = 0, RULE_idx = 1, RULE_tok_part = 2, RULE_token = 3;

    private static String[] makeRuleNames() {
        return new String[]{
                "tok_string", "idx", "tok_part", "token"
        };
    }

    public static final String[] ruleNames = makeRuleNames();

    private static String[] makeLiteralNames() {
        return new String[]{
                null, "'['", "']'", "'.'"
        };
    }

    private static final String[] _LITERAL_NAMES = makeLiteralNames();

    private static String[] makeSymbolicNames() {
        return new String[]{
                null, null, null, null, "INT", "TXT", "NEWLINE"
        };
    }

    private static final String[] _SYMBOLIC_NAMES = makeSymbolicNames();
    public static final Vocabulary VOCABULARY = new VocabularyImpl(_LITERAL_NAMES, _SYMBOLIC_NAMES);

    /**
     * @deprecated Use {@link #VOCABULARY} instead.
     */
    @Deprecated
    public static final String[] tokenNames;

    static {
        tokenNames = new String[_SYMBOLIC_NAMES.length];
        for (int i = 0; i < tokenNames.length; i++) {
            tokenNames[i] = VOCABULARY.getLiteralName(i);
            if (tokenNames[i] == null) {
                tokenNames[i] = VOCABULARY.getSymbolicName(i);
            }

            if (tokenNames[i] == null) {
                tokenNames[i] = "<INVALID>";
            }
        }
    }

    @Override
    @Deprecated
    public String[] getTokenNames() {
        return tokenNames;
    }

    @Override

    public Vocabulary getVocabulary() {
        return VOCABULARY;
    }

    @Override
    public String getGrammarFileName() {
        return "Token.g4";
    }

    @Override
    public String[] getRuleNames() {
        return ruleNames;
    }

    @Override
    public String getSerializedATN() {
        return _serializedATN;
    }

    @Override
    public ATN getATN() {
        return _ATN;
    }

    public TokenParser(TokenStream input) {
        super(input);
        _interp = new ParserATNSimulator(this, _ATN, _decisionToDFA, _sharedContextCache);
    }

    @SuppressWarnings("CheckReturnValue")
    public static class Tok_stringContext extends ParserRuleContext {
        public TokenContext token() {
            return getRuleContext(TokenContext.class, 0);
        }

        public TerminalNode EOF() {
            return getToken(TokenParser.EOF, 0);
        }

        public Tok_stringContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_tok_string;
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof TokenVisitor) return ((TokenVisitor<? extends T>) visitor).visitTok_string(this);
            else return visitor.visitChildren(this);
        }
    }

    public final Tok_stringContext tok_string() throws RecognitionException {
        Tok_stringContext _localctx = new Tok_stringContext(_ctx, getState());
        enterRule(_localctx, 0, RULE_tok_string);
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(8);
                token(0);
                setState(9);
                match(EOF);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    @SuppressWarnings("CheckReturnValue")
    public static class IdxContext extends ParserRuleContext {
        public TerminalNode INT() {
            return getToken(TokenParser.INT, 0);
        }

        public IdxContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_idx;
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof TokenVisitor) return ((TokenVisitor<? extends T>) visitor).visitIdx(this);
            else return visitor.visitChildren(this);
        }
    }

    public final IdxContext idx() throws RecognitionException {
        IdxContext _localctx = new IdxContext(_ctx, getState());
        enterRule(_localctx, 2, RULE_idx);
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(11);
                match(T__0);
                setState(12);
                match(INT);
                setState(13);
                match(T__1);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    @SuppressWarnings("CheckReturnValue")
    public static class Tok_partContext extends ParserRuleContext {
        public TerminalNode TXT() {
            return getToken(TokenParser.TXT, 0);
        }

        public Tok_partContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_tok_part;
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof TokenVisitor) return ((TokenVisitor<? extends T>) visitor).visitTok_part(this);
            else return visitor.visitChildren(this);
        }
    }

    public final Tok_partContext tok_part() throws RecognitionException {
        Tok_partContext _localctx = new Tok_partContext(_ctx, getState());
        enterRule(_localctx, 4, RULE_tok_part);
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(15);
                match(TXT);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    @SuppressWarnings("CheckReturnValue")
    public static class TokenContext extends ParserRuleContext {
        public Tok_partContext tok_part() {
            return getRuleContext(Tok_partContext.class, 0);
        }

        public List<TokenContext> token() {
            return getRuleContexts(TokenContext.class);
        }

        public TokenContext token(int i) {
            return getRuleContext(TokenContext.class, i);
        }

        public IdxContext idx() {
            return getRuleContext(IdxContext.class, 0);
        }

        public TokenContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_token;
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof TokenVisitor) return ((TokenVisitor<? extends T>) visitor).visitToken(this);
            else return visitor.visitChildren(this);
        }
    }

    public final TokenContext token() throws RecognitionException {
        return token(0);
    }

    private TokenContext token(int _p) throws RecognitionException {
        ParserRuleContext _parentctx = _ctx;
        int _parentState = getState();
        TokenContext _localctx = new TokenContext(_ctx, _parentState);
        TokenContext _prevctx = _localctx;
        int _startState = 6;
        enterRecursionRule(_localctx, 6, RULE_token, _p);
        try {
            int _alt;
            enterOuterAlt(_localctx, 1);
            {
                {
                    setState(18);
                    tok_part();
                }
                _ctx.stop = _input.LT(-1);
                setState(27);
                _errHandler.sync(this);
                _alt = getInterpreter().adaptivePredict(_input, 1, _ctx);
                while (_alt != 2 && _alt != org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER) {
                    if (_alt == 1) {
                        if (_parseListeners != null) triggerExitRuleEvent();
                        _prevctx = _localctx;
                        {
                            setState(25);
                            _errHandler.sync(this);
                            switch (getInterpreter().adaptivePredict(_input, 0, _ctx)) {
                                case 1: {
                                    _localctx = new TokenContext(_parentctx, _parentState);
                                    pushNewRecursionContext(_localctx, _startState, RULE_token);
                                    setState(20);
                                    if (!(precpred(_ctx, 3)))
                                        throw new FailedPredicateException(this, "precpred(_ctx, 3)");
                                    setState(21);
                                    match(T__2);
                                    setState(22);
                                    token(4);
                                }
                                break;
                                case 2: {
                                    _localctx = new TokenContext(_parentctx, _parentState);
                                    pushNewRecursionContext(_localctx, _startState, RULE_token);
                                    setState(23);
                                    if (!(precpred(_ctx, 2)))
                                        throw new FailedPredicateException(this, "precpred(_ctx, 2)");
                                    setState(24);
                                    idx();
                                }
                                break;
                            }
                        }
                    }
                    setState(29);
                    _errHandler.sync(this);
                    _alt = getInterpreter().adaptivePredict(_input, 1, _ctx);
                }
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            unrollRecursionContexts(_parentctx);
        }
        return _localctx;
    }

    public boolean sempred(RuleContext _localctx, int ruleIndex, int predIndex) {
        switch (ruleIndex) {
            case 3:
                return token_sempred((TokenContext) _localctx, predIndex);
        }
        return true;
    }

    private boolean token_sempred(TokenContext _localctx, int predIndex) {
        switch (predIndex) {
            case 0:
                return precpred(_ctx, 3);
            case 1:
                return precpred(_ctx, 2);
        }
        return true;
    }

    public static final String _serializedATN =
            "\u0004\u0001\u0006\u001f\u0002\u0000\u0007\u0000\u0002\u0001\u0007\u0001" +
                    "\u0002\u0002\u0007\u0002\u0002\u0003\u0007\u0003\u0001\u0000\u0001\u0000" +
                    "\u0001\u0000\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0002" +
                    "\u0001\u0002\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0003" +
                    "\u0001\u0003\u0001\u0003\u0001\u0003\u0005\u0003\u001a\b\u0003\n\u0003" +
                    "\f\u0003\u001d\t\u0003\u0001\u0003\u0000\u0001\u0006\u0004\u0000\u0002" +
                    "\u0004\u0006\u0000\u0000\u001c\u0000\b\u0001\u0000\u0000\u0000\u0002\u000b" +
                    "\u0001\u0000\u0000\u0000\u0004\u000f\u0001\u0000\u0000\u0000\u0006\u0011" +
                    "\u0001\u0000\u0000\u0000\b\t\u0003\u0006\u0003\u0000\t\n\u0005\u0000\u0000" +
                    "\u0001\n\u0001\u0001\u0000\u0000\u0000\u000b\f\u0005\u0001\u0000\u0000" +
                    "\f\r\u0005\u0004\u0000\u0000\r\u000e\u0005\u0002\u0000\u0000\u000e\u0003" +
                    "\u0001\u0000\u0000\u0000\u000f\u0010\u0005\u0005\u0000\u0000\u0010\u0005" +
                    "\u0001\u0000\u0000\u0000\u0011\u0012\u0006\u0003\uffff\uffff\u0000\u0012" +
                    "\u0013\u0003\u0004\u0002\u0000\u0013\u001b\u0001\u0000\u0000\u0000\u0014" +
                    "\u0015\n\u0003\u0000\u0000\u0015\u0016\u0005\u0003\u0000\u0000\u0016\u001a" +
                    "\u0003\u0006\u0003\u0004\u0017\u0018\n\u0002\u0000\u0000\u0018\u001a\u0003" +
                    "\u0002\u0001\u0000\u0019\u0014\u0001\u0000\u0000\u0000\u0019\u0017\u0001" +
                    "\u0000\u0000\u0000\u001a\u001d\u0001\u0000\u0000\u0000\u001b\u0019\u0001" +
                    "\u0000\u0000\u0000\u001b\u001c\u0001\u0000\u0000\u0000\u001c\u0007\u0001" +
                    "\u0000\u0000\u0000\u001d\u001b\u0001\u0000\u0000\u0000\u0002\u0019\u001b";
    public static final ATN _ATN =
            new ATNDeserializer().deserialize(_serializedATN.toCharArray());

    static {
        _decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
        for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
            _decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
        }
    }
}