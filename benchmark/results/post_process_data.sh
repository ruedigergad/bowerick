#!/bin/sh

DATA_SET_NAME=$1

grep -e "Running benchmark" -e "Execution time mean" ${DATA_SET_NAME}.raw | sed '$!N;s/\n/ /' | awk '{print $3,($8 * ($9=="ns" ? 1 : ($9=="µs" ? 1000 : 1000000)))}' | grep -v -e single -e simple -e "^ " -e "^ms" -e "^µs" | sed 's/-\([0-9]\)/ \1/g' | awk '{print $2,$3 > ($1 ".out_tmp")}'

mkdir $DATA_SET_NAME

for i in *.out_tmp
do
    tail -n +2 $i > ${DATA_SET_NAME}/$(basename -s .out_tmp $i).data
done

rm -f *.out_tmp

