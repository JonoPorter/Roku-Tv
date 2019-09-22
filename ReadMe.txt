Notice:
I did not write the original driver/device handler.
My Copywrite applies to the changes I have made. 
Original Source: https://github.com/apwelsh/hubitat


Install:
Add both groovy files as "Drivers Code"
Go to Devices and Create and new "Roku Tv" Device with the correct IP 


Change log 1.0:
Optimized web calls to be async <- massive speed improvement on hubitat
Added ability to use Child devices for Buttons
Added Ability to filter out unwanted apps as devices

Change log 1.1:
Fixed bug with deleting child apps when turning them all off. 