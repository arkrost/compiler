grammar Pascal;

program : 'program' ID ';' body '.' ;
body    : var_declaration_part function_declaration_part block ;

var_declaration_part    : ('var' (var_declaration ';')+ )? ;
var_declaration         : ID (',' ID)* ':' type ;

function_declaration_part   : function_declaration* ;
param_declaration           : ID (',' ID)* ':' PRIMITIVE_TYPE ;
function_declaration        :
 'function' ID ('(' param_declaration* ')')? ':' PRIMITIVE_TYPE ';' var_declaration_part block ';' ;


block           : 'begin' (statement ';')* 'end' ;
function_call   : ID '(' expression (',' expression)* ')' ;
qualified_name  : ID ('[' expression (',' expression)* ']')? ;

statement   : if_statement
            | for_statement
            | while_statement
            | assignment_statement
            | block
            | function_call
            | read_statement
            | write_statement
            | break_statement
            | continue_statement ;

if_statement        : 'if' expression 'then' statement else_part? ;
else_part           : 'else' statement ;
for_statement       : 'for' assignment_statement DIRECTION expression 'do' statement ;
while_statement     : 'while' expression 'do' statement ;
assignment_statement: qualified_name ':=' expression ;
read_statement      : 'read' '(' ID (',' ID)* ')' ;
write_statement     : 'write' '(' expression (',' expression)* ')' ;
break_statement     : 'break' ;
continue_statement  : 'continue' ;

expression  : app_term (CMP_OP app_term)? ;
app_term    : SIGN? mul_term (SIGN mul_term)* ;
mul_term    : factor (MUL_OP factor)* ;

factor      : NUMBER
            | qualified_name
            | function_call
            | '(' expression ')' ;

type    : PRIMITIVE_TYPE | 'array' '[' range (',' range)* ']' 'of' PRIMITIVE_TYPE ;
range   : NUMBER '..' NUMBER ;
PRIMITIVE_TYPE  : 'integer' ;

DIRECTION   : 'to' | 'downto' ;
SIGN        : '+' | '-' ;
CMP_OP      : '>=' | '<=' | '=' | '<>' | '>' | '<' ;
MUL_OP      : '*' | '/' | 'div' | 'mod';
ID          : [a-zA-Z][a-zA-Z0-9]* ;
NUMBER      : [0-9]+ ;
WS          : [ \t\r\n]+ -> skip ;