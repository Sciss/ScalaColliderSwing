#!/bin/sh
cd "`dirname $0`"
java -Xdock:icon=icons/application.png -Xmx1024m -jar ScalaCollider.jar "$@"
