program Foo;
var
 i, j : integer;
 a : array [1 .. 20, 3 .. 5] of integer;

function bar(a, b: integer) : integer;
var
    zz : array [1 .. 10] of integer;
begin
    zz[3] := 5;
    write(-zz[3] * a + b);
    bar := a;
end;


begin
   write(bar(3, 4));
end.
