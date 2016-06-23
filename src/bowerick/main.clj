;;;
;;;   Copyright 2016, Ruediger Gad
;;;   Copyright 2014, 2015 Frankfurt University of Applied Sciences
;;;
;;;   This software is released under the terms of the Eclipse Public License 
;;;   (EPL) 1.0. You can find a copy of the EPL at: 
;;;   http://opensource.org/licenses/eclipse-1.0.php
;;;

(ns
  ^{:author "Ruediger Gad",
    :doc "Main class to start the bowerick stand-alone application."}
  bowerick.main
  (:use
    [cheshire.core :only [generate-string parse-string]])
  (:require
    [bowerick.jms :refer :all]
    [cli4clj.cli :refer :all]
    [clj-assorted-utils.util :refer :all]
    [clojure.pprint :refer :all]
    [clojure.string :as s]
    [clojure.tools.cli :refer :all])
  (:gen-class))

(defn start-broker-mode
  [arg-map]
  (println-err "Starting bowerick in broker mode.")
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
        (shutdown-fn)))))

(def url-format-help-string
  (str
    "The URL format is: <PROTOCOL>://<ADDRESS>:<PORT>:/[topic,queue]/<NAME>"
    "\n\t<PROTOCOL> can be, e.g.: tcp, udp, stomp, ssl or stomp+ssl"
    "\n\t<ADDRESS> is the IP address or name of the broker."
    "\n\t<PORT> is the port number on which the broker listens."
    "\n\t<NAME> is the name of the topic/queue to which the data will be sent."
    "\n\tAn example of an URL is: tcp://localhost:61616:/topic/test.topic.name"
    ))

(defn create-cached-endpoint
  [cache url endpoint-factory-fn & args]
  (when (not (@cache url))
    (swap!
      cache
      assoc
      url
      (apply
        endpoint-factory-fn
        (conj
          args
          (second (s/split (str url) #":(?=/[^/])"))
          (first (s/split (str url) #":(?=/[^/])")))))))

(defn start-client-mode
  [arg-map]
  (println-err "Starting bowerick in client mode.")
  (let [consumers (atom {})
        producers (atom {})
        out-binding *out*]
    (start-cli {:cmds
                 {:send {:fn (fn [url data]
                               (create-cached-endpoint producers url create-producer)
                               (println "Sending:" url "<-")
                               (pprint data)
                               ((@producers url) data))
                         :short-info "Send data to URL."
                         :long-info (str
                                      url-format-help-string)}
                  :s :send
                  :receive {:fn (fn [url]
                                  (create-cached-endpoint
                                    consumers
                                    url
                                    create-consumer
                                    (fn [rcvd]
                                      (binding [*out* out-binding]
                                        (println "Received:" url "->")
                                        (pprint rcvd))))
                                  (println "Set up consumer for:" url))
                         :short-info "Set up a consumer for receiving data from URL."
                         :long-info (str
                                      url-format-help-string)}
                  :r :receive
                  :management {:fn (fn [url command & args])}
                  }
                :prompt-string "bowerick# "})))

(defn -main [& args]
  (let [cli-args (cli args
                      ["-c" "--client" "Start in client mode." :flag true :default false]
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
    (binding [*out* *err*]
      (println "Starting bowerick using the following options:")
      (pprint arg-map)
      (pprint extra-args))
    (if (:client arg-map)
      (start-client-mode arg-map)
      (start-broker-mode arg-map))))

