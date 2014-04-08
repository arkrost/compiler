program test3;

var  x, y, z, i, n : integer; 

begin 

	read (n); 

	x:=1; 

	y:=0; 

	for   i:=1 to n do 

	begin 

		z:=x;  

		x:=x+y;  

		y:=z; 

		write (x); 

	end; 
end.
