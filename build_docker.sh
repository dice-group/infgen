#!/bin/sh
mvn compile jib:dockerBuild "$@"
