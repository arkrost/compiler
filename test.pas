program Foo;
var
 i, j : integer;
 a : array [1 .. 20, 3 .. 5] of integer;
begin
    for i := 10 downto 1 do
        for a[4, 4] := 1 to i do write(i * a[4,4]);
end.
