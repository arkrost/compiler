program Foo;
var
 i, j : integer;
 a : array [1 .. 20, 3 .. 5] of integer;

function bar(a, b: integer) : integer;
begin
    write(a + b);
    bar := a;
end;


begin
   write(bar(3, 4));
end.
