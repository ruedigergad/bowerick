;;;
;;;   Copyright 2014, Frankfurt University of Applied Sciences
;;;
;;;   This software is released under the terms of the Eclipse Public License 
;;;   (EPL) 1.0. You can find a copy of the EPL at: 
;;;   http://opensource.org/licenses/eclipse-1.0.php
;;;

(ns
  ^{:author "Ruediger Gad",
    :doc "Main class to start a simple ActiveMQ broker instance."}
  bowerick.main
  (:use
    [cheshire.core :only [generate-string parse-string]])
  (:require
    [bowerick.jms :refer :all]
    [clojure.pprint :refer :all]
    [clojure.tools.cli :refer :all])
  (:gen-class))

(defn -main [& args]
  (let [cli-args (cli args
                      ["-d" "--daemon" "Run as daemon." :flag true :default false]
                      ["-h" "--help" "Print this help." :flag true]
                      ["-u" "--url" 
                       "URL to bind the broker to." 
                       :default "tcp://localhost:61616"
                       :parse-fn #(binding [*read-eval* false]
                                    (read-string) %)])
        arg-map (cli-args 0)
        extra-args (cli-args 1)
        help-string (cli-args 2)]
    (when (arg-map :help)
      (println help-string)
      (System/exit 0))
    (println "Starting ActiveMQ Broker using the following options:")
    (pprint arg-map)
    (pprint extra-args)
    (let [url (arg-map :url)
          broker-service (start-broker url)
          shutdown-fn (fn []
                        (stop broker-service))]
      (if (:daemon arg-map)
        (-> (agent 0) (await))
        (do
          (println "Broker started... Type \"q\" followed by <Return> to quit: ")
          (while (not= "q" (read-line))
            (println "Type \"q\" followed by <Return> to quit: "))
          (println "Shutting down...")
          (shutdown-fn))))))

