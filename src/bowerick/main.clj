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
  (:require
    [bowerick.jms :refer :all]
    [bowerick.message-generator :refer :all]
    [cli4clj.cli :refer :all]
    [clj-assorted-utils.util :refer :all]
    [clojure.pprint :refer :all]
    [clojure.string :as s]
    [clojure.tools.cli :refer :all])
  (:gen-class))

(defn start-broker-mode
  [arg-map]
  (println-err "Starting bowerick in broker mode.")
  (let [arg-url (arg-map :url)
        af-demo-url "ws://127.0.0.1:1864"
        url (if (arg-map :a-frame-demo)
              (if (string? arg-url)
                [arg-url af-demo-url]
                (conj arg-url af-demo-url))
              arg-url)
        broker-service (start-broker url)
        running (atom true)
        shutdown-fn (fn []
                      (println "Shutting down...")
                      (reset! running false)
                      (sleep 500)
                      (println "Stopping broker...")
                      (stop broker-service))]
    (if (arg-map :a-frame-demo)
      (let [af-topic-name "/topic/aframe"
            af-prod (create-producer af-demo-url af-topic-name 1)
            max_angle (* 2.0 Math/PI)
            angle_increment (/ max_angle 100.0)]
        (println "Starting producer for the A-Frame demo at:" (str af-demo-url ":" af-topic-name))
        (doto (Thread. #(loop [angle 0.0]
                          (let [x (Math/cos angle)
                                y (Math/sin angle)]
                            (af-prod {:x x, :y y, :z 0})
                            (sleep 20)
                            (let [new_angle (+ angle angle_increment)]
                              (if @running
                                (if (> new_angle max_angle)
                                  (recur (+ 0.0 (- max_angle new_angle)))
                                  (recur new_angle))
                                (do
                                  (println "Stopping A-Frame producer...")
                                  (close af-prod)))))))
          (.setDaemon true)
          (.start))
        (println "To view the example, open the following web page:")
        (println "http://ruedigergad.github.io/bowerick/examples/simple_websocket_a-frame_example/simple_websocket_a-frame_example.html")
        (println "Alternatively, if you have the bowerick source code repository, you can also open the page from the checked out repository:")
        (println "examples/simple_websocket_a-frame_example/simple_websocket_a-frame_example.html")))
    (if (string? (arg-map :embedded-message-generator))
      (let [msg-gen-prod (create-producer
                           (if (string? url)
                             url
                             (first url))
                           (arg-map :generator-destination)
                           (arg-map :generator-producer-pool-size))
            msg-gen-prod-fn (fn [data] (if @running (msg-gen-prod data)))
            msg-delay (arg-map :generator-interval)
            counter (atom 0)
            delay-fn (if (and (integer? msg-delay) (pos? msg-delay))
                       #(do (swap! counter inc) (sleep msg-delay))
                       #(swap! counter inc))
            generator-name (arg-map :embedded-message-generator)
            msg-gen (create-message-generator
                      msg-gen-prod-fn
                      delay-fn
                      generator-name
                      (arg-map :generator-arguments))]
        (println "Starting message generation with:" generator-name
                 ", sending to:" (arg-map :generator-destination)
                 ", with args:" (arg-map :generator-arguments))
        (doto
          (Thread. #(loop []
                      (msg-gen)
                      (if @running
                        (recur)
                        (do
                          (println "Stopping message-generator producer...")
                          (close msg-gen-prod)))))
          (.setDaemon true)
          (.start))
        (doto
          (Thread. #(loop []
                      (println "data instances per second:" @counter)
                      (reset! counter 0)
                      (sleep 1000)
                      (if @running
                        (recur))))
          (.setDaemon true)
          (.start))))
    (if (arg-map :daemon)
      (-> (agent 0) (await))
      (do
        (println "Broker started... Type \"q\" followed by <Return> to quit: ")
        (while (not= "q" (read-line))
          (println "Type \"q\" followed by <Return> to quit: "))
        (shutdown-fn)))))

(def destination-url-format-help-string
  (str
    "The destination-url format is: <PROTOCOL>://<ADDRESS>:<PORT>:/[topic,queue]/<NAME>"
    "\n\t<PROTOCOL> can be, e.g.: tcp, udp, stomp, ssl, or stomp+ssl"
    "\n\t<ADDRESS> is the IP address or name of the broker."
    "\n\t<PORT> is the port number on which the broker listens."
    "\n\t<NAME> is the name of the topic/queue to which the data will be sent."
    "\n\tAn example of a destintation-url is: tcp://localhost:61616:/topic/test.topic.name"
    ))

(defn create-cached-destination
  [cache destination-url destination-factory-fn & args]
  (when (not (@cache destination-url))
    (swap!
      cache
      assoc
      destination-url
      (apply
        destination-factory-fn
        (conj
          args
          (second (s/split (str destination-url) #":(?=/[^/])"))
          (first (s/split (str destination-url) #":(?=/[^/])")))))))

(defn start-client-mode
  [arg-map]
  (println-err "Starting bowerick in client mode.")
  (let [consumers (atom {})
        producers (atom {})
        out-binding *out*]
    (start-cli {:cmds
                 {:send {:fn (fn [destination-url data]
                               (create-cached-destination producers destination-url create-json-producer)
                               (println "Sending:" destination-url "<-")
                               (pprint data)
                               ((@producers destination-url) data))
                         :short-info "Send data to destination."
                         :long-info (str
                                      destination-url-format-help-string)}
                  :s :send
                  :receive {:fn (fn [destination-url]
                                  (create-cached-destination
                                    consumers
                                    destination-url
                                    create-failsafe-json-consumer
                                    (fn [rcvd]
                                      (binding [*out* out-binding]
                                        (println "Received:" destination-url "->")
                                        (pprint rcvd))))
                                  (println "Set up consumer for:" destination-url))
                         :short-info "Set up a consumer for receiving data from destintation."
                         :long-info (str
                                      destination-url-format-help-string)}
                  :r :receive
                  :management {:fn (fn [broker-url command & args]
                                     (create-cached-destination
                                       consumers
                                       (str broker-url ":" *broker-management-reply-topic*)
                                       create-failsafe-json-consumer
                                       (fn [reply-str]
                                         (binding [*out* out-binding
                                                   *read-eval* false]
                                           (println "Management Reply:" broker-url "->")
                                           (try
                                             (let [reply-obj (read-string reply-str)]
                                               (if (instance? clojure.lang.Symbol reply-obj)
                                                 (println reply-str)
                                                 (pprint reply-obj)))
                                             (catch Exception e
                                               (println "Error reading reply:" (.getMessage e))
                                               (println "Printing raw reply below:")
                                               (println reply-str))))))
                                     (let [cmd-destination (str broker-url ":" *broker-management-command-topic*)
                                           cmd-with-args (str command (reduce #(str %1 " " %2) "" args))]
                                       (create-cached-destination producers cmd-destination create-json-producer)
                                       (println "Management Command:" cmd-destination "<-")
                                       (pprint cmd-with-args)
                                       ((@producers cmd-destination) cmd-with-args)))}
                  :m :management
                  }
                :prompt-string "bowerick# "})
    (doseq [producer (vals @producers)]
      (close producer))
    (doseq [consumer (vals @consumers)]
      (close consumer))))

(defn start-benchmark-client-mode [arg-map]
  (let [counter (atom 0)
        consumer-fn (fn [_] (swap! counter inc))
        running (atom true)
        output-thread (doto (Thread. #(loop []
                                        (println "data instances per second:" @counter)
                                        (reset! counter 0)
                                        (sleep 1000)
                                        (if @running
                                          (recur))))
                        (.setDaemon true)
                        (.start))
        consumer (create-consumer (arg-map :url) (arg-map :benchmark-client) consumer-fn (arg-map :pool-size))
        shutdown-fn (fn []
                      (reset! running false)
                      (close consumer))]
      (do
        (println "Benchmark client started... Type \"q\" followed by <Return> to quit: ")
        (while (not= "q" (read-line))
          (println "Type \"q\" followed by <Return> to quit: "))
        (shutdown-fn))))

(defn -main [& args]
  (let [cli-args (cli args
                   ["-b" "--benchmark-client"
                    "Start a benchmark client listening at the given destination."
                    :default nil]
                   ["-c" "--client" "Start in client mode." :flag true :default false]
                   ["-d" "--daemon" "Run as daemon." :flag true :default false]
                   ["-h" "--help" "Print this help." :flag true]
                   ["-p" "--pool-size"
                    "The consumer pool size when using the benchmark client."
                    :default 1
                    :parse-fn #(Integer/decode %)]
                   ["-u" "--url"
                     "URL to bind the broker to."
                     :default "tcp://localhost:61616"
                     :parse-fn #(binding [*read-eval* false]
                                  (read-string %))]
                   ["-A" "--a-frame-demo"
                     "When in daemon mode, start a producer for the A-Frame demo."
                     :flag true :default false]
                   ["-D" "--generator-destination"
                     "The destination to which message generators will send messages."
                     :default "/topic/bowerick.message.generator"]
                   ["-G" "--embedded-message-generator"
                     "When in daemon mode, start the specified embedded message generator."
                     :default nil]
                   ["-I" "--generator-interval"
                     "The interval with which messages shall (roughly) be generated in milliseconds."
                     :default 10
                     :parse-fn #(Integer/decode %)]
                   ["-P" "--generator-producer-pool-size"
                     "The pool size for the message generator producer."
                     :default 1
                     :parse-fn #(Integer/decode %)]
                   ["-X" "--generator-arguments"
                     "Arguments for message generators that accept arguments."
                     :default nil])
        arg-map (cli-args 0)
        extra-args (cli-args 1)
        help-string (cli-args 2)]
    (if (arg-map :help)
      (do
        (println "Bowerick help:")
        (println help-string))
      (do
        (binding [*out* *err*]
          (println "Starting bowerick using the following options:")
          (pprint arg-map)
          (pprint extra-args))
        (cond
          (arg-map :client) (start-client-mode arg-map)
          (not (nil? (arg-map :benchmark-client))) (start-benchmark-client-mode arg-map)
          :default (start-broker-mode arg-map))))))

