program Foo;
var
 i, j : integer;
 a : array [1 .. 20, 3 .. 5] of integer;

function isEven(i : integer) : integer;
begin
    isEven := (i / 2) * 2 = i;
end;

begin
   read(j);
   for i := j to 20 do
   begin
        if isEven(i) then
            continue
        else if i > 11 then
            break;
        write(i);
   end;
end.
