program test6;
var
 i, j : integer;
 b : boolean;
begin
    read(i, j);
    b := (i < j) and (i * i > j) or false;
    write(b);
end.
