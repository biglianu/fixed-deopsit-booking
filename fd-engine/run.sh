#!/usr/bin/env bash
set -e
rm -rf out output
javac -d out $(find src/main src/test -name "*.java")
java -cp out fd.FdTests
mkdir -p output
java -cp out fd.Main data/ratecard.csv data/holidays.csv data/commands.txt output/
cat output/events.log
