// Generated from Token.g4 by ANTLR 4.12.0

package com.syncari.core.token.parser;

import org.antlr.v4.runtime.tree.ParseTreeVisitor;

/**
 * This interface defines a complete generic visitor for a parse tree produced
 * by {@link TokenParser}.
 *
 * @param <T> The return type of the visit operation. Use {@link Void} for
 *            operations with no return type.
 */
public interface TokenVisitor<T> extends ParseTreeVisitor<T> {
    /**
     * Visit a parse tree produced by {@link TokenParser#tok_string}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitTok_string(TokenParser.Tok_stringContext ctx);

    /**
     * Visit a parse tree produced by {@link TokenParser#idx}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitIdx(TokenParser.IdxContext ctx);

    /**
     * Visit a parse tree produced by {@link TokenParser#tok_part}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitTok_part(TokenParser.Tok_partContext ctx);

    /**
     * Visit a parse tree produced by {@link TokenParser#token}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitToken(TokenParser.TokenContext ctx);
}