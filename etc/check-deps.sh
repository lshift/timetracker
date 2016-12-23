#!/bin/bash
lein deps :tree > tree 2>&1
grep "Consider using these exclusions" tree > /dev/null
if [ "x$?" == "x0" ]; then
    cat tree
    exit 1
fi
