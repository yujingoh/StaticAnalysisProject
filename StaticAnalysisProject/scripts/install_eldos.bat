D:
cd D:\Projects\CloudComputing\Development\DiffusedCloudStorage\bin
set path=C:\Program Files\Java\jdk1.6.0_27\bin
set libpath=D:\Projects\CloudComputing\Development\DiffusedCloudStorage\lib
set classpath=.;%libpath%\commons-logging-1.1.1.jar;%libpath%\log4j-1.2.9.jar;%libpath%\eldos.cbfs.jar;
java -Djava.library.path=D:\Projects\CloudComputing\Development\DiffusedCloudStorage\lib;D:\Projects\CloudComputing\Development\DiffusedCloudStorage\lib\eldos_x64 sg.edu.nyp.sit.pvfs.virtualdisk.eldos.sample.SampleEldos
pause