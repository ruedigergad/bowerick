#!/bin/sh

DATA_SET_NAME=$1
BENCHMARK_SET=$2

cp plot_template.gpl ${DATA_SET_NAME}/${BENCHMARK_SET}.gpl

cd ${DATA_SET_NAME}

for i in *${BENCHMARK_SET}*.data
do
    echo '    "'$(basename $i)'" using 1:($2/1000) with linespoints lw 2.5 ps 1 title "'$(basename -s .data $i)'",\' >> ${BENCHMARK_SET}.gpl
done

echo "" >> ${BENCHMARK_SET}.gpl

gnuplot -e 'set output "'${BENCHMARK_SET}'.pdf"' ${BENCHMARK_SET}.gpl
convert -flatten -density 150 -quality 100 -sharpen 0x1.0 ${BENCHMARK_SET}.pdf ${BENCHMARK_SET}.png

cd ..

