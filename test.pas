program Foo;
var
 i, j : integer;
 a : array [1 .. 20, 3 .. 5] of integer;

function bar(a, b: integer) : integer;
var
    k : integer;
    zz : array [1 .. 10] of integer;
begin
    zz[3] := 5;
    read(k);
    write(-zz[3] * a + k);
    bar := a;
end;


begin
   read(i);
   write(bar(i, 4));
end.
