program Foo;
var
 i, j : integer;
 a : array [1 .. 20] of integer;

function isEven(i : integer) : boolean;
begin
    isEven := (i / 2) * 2 = i;
end;

function square(i : integer) : integer;
begin
    square := i * i;
end;

function squareArrayElements(arr : array[1 .. 20 ] of integer) : array[1..20] of integer;
var tmp : array[1..20] of integer;
    i : integer;
begin
   for i := 1 to 20 do
     tmp[i] := square(arr[i]);
   squareArrayElements := tmp;
end;

begin
   for i := 1 to 20 do
   begin
        if isEven(i) then a[i] := i * 10 else a[i] := i;
        write(a[i]);
   end;
   a := squareArrayElements(a);
   for i := 1 to 20 do write(a[i]);
end.
