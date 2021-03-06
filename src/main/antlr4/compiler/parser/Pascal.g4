grammar Pascal;

program : 'program' ID ';' body '.' ;
body    : varDeclarations functionDeclarations block ;

varDeclarations : ('var' (varDeclaration ';')+ )? ;
varDeclaration  : ID (',' ID)* ':' type ;

functionDeclarations   : functionDeclaration* ;
functionDeclaration    :
 'function' ID ('(' varDeclaration* ')')? ':' type ';' varDeclarations block ';' ;


block           : 'begin' (statement ';')* 'end' ;
functionCall   : ID '(' (expression (',' expression)*)? ')' ;
qualifiedName  : ID ('[' expression (',' expression)* ']')? ;

statement   : ifStatement
            | forStatement
            | whileStatement
            | assignmentStatement
            | block
            | functionCall
            | readStatement
            | writeStatement
            | breakStatement
            | continueStatement ;

ifStatement        : 'if' expression 'then' statement elsePart? ;
elsePart           : 'else' statement ;
forStatement       : 'for' assignmentStatement DIRECTION expression 'do' statement ;
whileStatement     : 'while' expression 'do' statement ;
assignmentStatement: qualifiedName ':=' expression ;
readStatement      : 'read' '(' qualifiedName (',' qualifiedName)* ')' ;
writeStatement     : 'write' '(' expression (',' expression)* ')' ;
breakStatement     : 'break' ;
continueStatement  : 'continue' ;

expression  : appTerm (APP_OP appTerm)* ;
appTerm     : SIGN? mulTerm (SIGN mulTerm)* ;
mulTerm     : factor (MUL_OP factor)* ;

factor      : NUMBER
            | bool
            | qualifiedName
            | functionCall
            | '(' expression ')'
            | notFactor ;

notFactor   : 'not' factor;
bool        : 'true' | 'false';

type    : PRIMITIVE_TYPE | 'array' '[' range (',' range)* ']' 'of' PRIMITIVE_TYPE ;
range   : NUMBER '..' NUMBER ;
PRIMITIVE_TYPE  : 'integer' | 'boolean';

DIRECTION   : 'to' | 'downto' ;
SIGN        : '+' | '-' ;
APP_OP      : '>=' | '<=' | '=' | '<>' | '>' | '<' | 'or' | 'and';
MUL_OP      : '*' | '/' | 'mod';
ID          : [a-zA-Z][a-zA-Z0-9]* ;
NUMBER      : [0-9]+ ;
WS          : [ \t\r\n]+ -> skip ;