all: compile run

compile:
	javac --release 8 sender.java

run:
	java sender 192.168.8.131 6500 hello.txt

clean:
	del *.class