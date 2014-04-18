program test6;
var
 i, j : integer;
 b : boolean;
begin
    read(i, j);
    if (i < j) and (i * i > j) or false then write(i + j) else write(j - i);
end.
