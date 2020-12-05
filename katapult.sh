#!/bin/bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"

#https://cdn.azul.com/zulu/bin/zulu8.42.0.23-ca-fx-jre8.0.232-linux_x64.tar.gz
JVERELEASE=zulu8.44.0.13-ca-fx-jdk8.0.242-linux_x64
#JVERELEASE=zulu8.42.0.23-ca-fx-jre8.0.232-linux_x64
JVMDIR=$HOME/bin/java8/
JAR=BowlerStudio.jar
AUTOUPDATE=$HOME/bin/java8/LatestFromGithubLaunch.jar

unzipifiy(){
	testget  $1 $2
	echo "Unzipping $1 to $2"
	tar -xzf $2/$1.tar.gz -C $2
	mv $2/$1/* $2/
	rmdir $2/$1/
}

testget () {
	if [ -f $JVERELEASE.tar.gz ]; then
		echo "$JVERELEASE.tar.gz exist"
		else
		echo Downloading from https://www.azul.com/downloads/zulu/zulufx/ 
		echo downloading https://cdn.azul.com/zulu/bin/$JVERELEASE.tar.gz
		wget https://cdn.azul.com/zulu/bin/$JVERELEASE.tar.gz  -O $2$1.tar.gz
	fi
}

if (! test -e $JVMDIR/$JVERELEASE.tar.gz) then
	rm -rf $JVMDIR
	mkdir -p $JVMDIR
	unzipifiy $JVERELEASE $JVMDIR
fi

$JVMDIR/bin/java -jar ~/bin/BowlerStudioInstall/1.12.0/BowlerStudio.jar -g https://github.com/OperationSmallKat/Katapult.git launch.groovy 