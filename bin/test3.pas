program test3;

var
	n, i: integer;
	a: array[1..100] of integer;

function isPrime(x: integer): integer;
var i, has: integer;
begin
	has := 0;
	for i := 2 to x - 1 do begin
		if x mod i = 0 then begin
			has := 1;
			break;
		end;
	end;
	isPrime := 1 - has;
end;

begin 

	read (n); 
	for i := n downto 1 do begin
		if isPrime(i) = 1 then begin
			write(i);
		end;
	end;

end.
