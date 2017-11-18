;;;
;;;   Copyright 2017, Ruediger Gad
;;;
;;;   This software is released under the terms of the Eclipse Public License 
;;;   (EPL) 1.0. You can find a copy of the EPL at: 
;;;   http://opensource.org/licenses/eclipse-1.0.php
;;;

(ns 
  ^{:author "Ruediger Gad",
    :doc "Helper functions for tests"}  
  bowerick.test.test-helper
  (:require
    [bowerick.jms :refer :all]
    [clj-assorted-utils.util :refer :all]))



(defn start-test-broker
  [& args]
  (let [brkr (atom nil)]
    (while (nil? @brkr)
      (try
        (reset! brkr (apply start-broker args))
        (catch java.net.BindException e
          (println "Test broker start failed with" (str e))
          (println "Retrying to start test broker...")
          (sleep 100))))
    @brkr))

