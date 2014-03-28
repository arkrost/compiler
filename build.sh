#!/bin/sh

lib=lib/antlr-4.2.1-complete.jar
args=${@:2}
case "$1" in
	antlr)
	java -cp $lib org.antlr.v4.Tool $args
	;;
	grun)
	java -cp $lib org.antlr.v4.runtime.misc.TestRig $args
	;;
	*)
	echo  $"Usage $0 {antlr|grun}"
	exit 1
esac
