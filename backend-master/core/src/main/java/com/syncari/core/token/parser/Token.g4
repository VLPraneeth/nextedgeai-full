grammar Token;
@header {
package com.syncari.core.token.parser;
}
tok_string:   token EOF ;
idx: '[' INT ']'
    ;
tok_part : TXT ;
token:  token '.' token
    |   token idx
    |   tok_part
    ;
INT     : [0-9]+ ;
TXT     : ('a'..'z' | 'A'..'Z'|' '| '$'|'#'|'!'|'@'|'%'|'^'|'&'|'?'|'|'|'*'|'_'|'-'|'\\.'|'0'..'9')+
        | INT
        ;
NEWLINE : [\r\n]+ -> skip;