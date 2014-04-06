program Foo;
var
 i, j : integer;
 a : array [1 .. 20, 3 .. 5] of integer;
begin
    i := 3;
    while i <= 5 do begin
        write(i);
        i := i + 1;
    end;
end.
