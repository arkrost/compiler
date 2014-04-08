program test2;

var

   n: array [1..10] of integer;

   i, j: integer;

begin     

   for i := 1 to 10 do

       n[ i ] := i + 100;

   for j:= 1 to 10 do

      write(n[j]);

end.
