#!/bin/bash

REPN_OLD="syncplus"
REPN_NEW=$1

TF=x.x

if [ -z "$REPN_NEW" ]; then echo "give me a reponame"; exit; fi
echo "$REPN_OLD -> $REPN_NEW"


find . -type f | while read FIL; do echo $FIL; mv $FIL $TF; cat $TF | sed "s/$REPN_OLD/$REPN_NEW/g" > $FIL; done
