;;;
;;;   Copyright 2017, Ruediger Gad
;;;
;;;   This software is released under the terms of the Eclipse Public License 
;;;   (EPL) 1.0. You can find a copy of the EPL at: 
;;;   http://opensource.org/licenses/eclipse-1.0.php
;;;

(ns
  ^{:author "Ruediger Gad",
    :doc "Main class for a simple event generator for the A-Frame example."}
  event-generator.main
  (:require
    [bowerick.jms :refer :all]
    [clj-assorted-utils.util :refer :all]
    [clojure.pprint :refer :all]
    [clojure.string :as s])
  (:import
    [java.lang Math])
  (:gen-class))

(defn -main [& args]
  (let [broker-url "ws://127.0.0.1:1864"
        topic-name "/topic/aframe"
        prod (create-producer broker-url topic-name 1)
        
        ]
    (loop [angle 0.0]
      (let [x (Math/cos angle)
            y (Math/sin angle)
            max_angle (* 2.0 Math/PI)
            angle_increment (/ max_angle 100.0)]
        (println "x =" x "; y =" y)
        (prod (str x " " y " -5"))
        (sleep 20)
        (let [new_angle (+ angle angle_increment)]
          (if (> new_angle max_angle)
            (recur (+ 0.0 (- max_angle new_angle)))
            (recur new_angle)))))))
