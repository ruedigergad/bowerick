#!/bin/sh

BOOTSTRAP_CERTS=${BOOTSTRAP_CERTS:-true}

URLS=${URLS:-"tcp://0.0.0.0:1031 mqtt://0.0.0.0:1701 ws://0.0.0.0:1864 stomp://0.0.0.0:2000 ssl://0.0.0.0:11031 stomp+ssl://0.0.0.0:11701 mqtt+ssl://0.0.0.0:11864 wss://0.0.0.0:12000"}

SET_PASSWORDS=${SET_PASSWORDS:-false}
export BOWERICK_ADMIN_PASS=${BOWERICK_ADMIN_PASS:-adminpass}
export BOWERICK_WRITE_PASS=${BOWERICK_WRITE_PASS:-writepass}
export BOWERICK_READ_PASS=${BOWERICK_READ_PASS:-readpass}

GEN_DESTINATION=${GEN_DESTINATION:-/topic/bowerick.message.generator}
GEN_TYPE=${GEN_TYPE:-custom-fn}
GEN_ARGS=${GEN_ARGS:-./generator.clj}
GEN_INTERVAL=${GEN_INTERVAL:-100}
GEN_FN=${GEN_FN:-"
(fn [producer delay-fn]
  (let [max_angle (* 2.0 Math/PI)
        angle_increment (/ max_angle 100.0)
        angle (atom 0.0)]
    (fn []
      (let [x (Math/cos @angle)
            y (Math/sin @angle)]
        (producer (cheshire.core/generate-string [{\"x\" x, \"y\" y, \"z\" 0.0}]))
        (delay-fn)
        (if (> @angle max_angle)
          (reset! angle (+ (- @angle max_angle) angle_increment))
          (reset! angle (+ @angle angle_increment)))))))"
}
echo "${GEN_FN}" > generator.clj

COMMAND="java -jar bowerick*standalone.jar -d -v"
if ${BOOTSTRAP_CERTS} ;
then
    COMMAND="${COMMAND} -b"
fi
if ${SET_PASSWORDS} ;
then
    COMMAND="${COMMAND} -e"
fi
COMMAND="${COMMAND} -D ${GEN_DESTINATION} -G ${GEN_TYPE} -X ${GEN_ARGS} -I ${GEN_INTERVAL}"
echo ${COMMAND} -u "${URLS}"
${COMMAND} -u "${URLS}"

