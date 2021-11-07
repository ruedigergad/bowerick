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
    [bowerick.jms :as jms]
    [bowerick.message-generator :refer :all]
    [cheshire.core :as cheshire]
    [cli4clj.cli :refer :all]
    [clj-assorted-utils.util :refer :all]
    [clojure.java.io :as jio]
    [clojure.pprint :refer :all]
    [clojure.string :as s]
    [clojure.tools.cli :refer :all]
    [juxt.dirwatch :refer (watch-dir)])
  (:import
    (java.nio.file Files Paths))
  (:gen-class))



(def license-info (->> (jio/resource "licenses_edn.txt") slurp read-string (sort-by (fn [[[name-sym _] _]] (str name-sym)))))
(def bowerick-license-text (str "Bowerick is licensed under the terms of the Eclipse Public License (EPL) 1.0.\n"
                                "----------------------\n"
                                "In addition, below, an overview of bowerick's (transitive) dependencies (not including Java) and their licenses is given:"))
(def license-text-files
  ["Apache_License_Version_2_0.txt"
   "BSD_License_Thoghtworks_XStream.txt"
   "BSD_License_Three-clause.txt"
   "BSD_License_Two-clause.txt"
   "Common_Development_and_Distribution_License_Version_1_0.txt"
   "Eclipse_Public_License_Version_1_0.txt"
   "Indiana_University_Extreme_Lab_Software_License_Vesion_1_1_1.txt"
   "MIT_License.txt"
   "javax-servlet-api_CDDL_plus_GPL_Licenses.txt"])

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
    (let [new-consumer (apply
                         destination-factory-fn
                         (conj
                           args
                           (second (s/split (str destination-url) #":(?=/[^/])"))
                           (first (s/split (str destination-url) #":(?=/[^/])"))))]
      (swap! cache assoc destination-url new-consumer)
      new-consumer)))

(defn replay [replay-file interval loop-send producers]
  (println "Replaying from file:" replay-file)
  (let [replay-data (cheshire/parse-string (slurp (str replay-file)))
        ref-time (get-in replay-data ["metadata" "timestamp_nanos"])
        msgs-by-time (reduce (fn [msgs entry] (assoc msgs (get-in entry ["metadata" "timestamp"]) entry)) {} (replay-data "messages"))
        timestamps (keys msgs-by-time)]
    (println "Replaying" (count msgs-by-time) "messages using reference time:" ref-time)
    (doto (Thread. (fn []
                     (loop [next-send-ts (first timestamps)]
                       (let [ts-to-send (if (nil? next-send-ts)
                                          []
                                          (filter
                                            (fn [itm]
                                              (and
                                                (>= itm next-send-ts)
                                                (<= itm (+ next-send-ts (* interval 1000000)))))
                                            timestamps))]
                         (doseq [ts ts-to-send]
                           (let [msg (msgs-by-time ts)
                                 data (msg "data")
                                 dest (get-in msg ["metadata" "source"])]
                             (when (not (contains? @producers dest))
                               (create-cached-destination producers dest jms/create-producer))
                             ((@producers dest) data)))
                         (sleep interval)
                         (if (and
                               loop-send
                               (= (last ts-to-send) (last timestamps)))
                           (recur (first timestamps))
                           (when (not= (last ts-to-send) (last timestamps))
                             (let [next-send-ts-candidate (if (empty? ts-to-send)
                                                            nil
                                                            (first
                                                              (filter
                                                                (fn [itm]
                                                                  (and
                                                                    (> itm (last ts-to-send))
                                                                    (<= itm (+ (last ts-to-send) (* interval 1000000)))))
                                                                timestamps)))
                                   new-next-send-ts (if (not (nil? next-send-ts-candidate))
                                                      next-send-ts-candidate
                                                      (if (empty? ts-to-send)
                                                        (+ next-send-ts (* interval 1000000))
                                                        (+ (max (last ts-to-send) next-send-ts) (* interval 1000000))))]
                               (recur new-next-send-ts))))))))
      (.setDaemon true)
      (.start)))
  nil)

(defn start-broker-mode
  [arg-map cfg]
  (println-err "Starting bowerick in broker mode.")
  (when (arg-map :bootstrap-self-signed-certs)
    (if (= jms/*key-store-file* "client.ks")
      (alter-var-root #'bowerick.jms/*key-store-file* (fn [_ x] x) "broker.ks"))
    (if (= jms/*trust-store-file* "client.ts")
      (alter-var-root #'bowerick.jms/*trust-store-file* (fn [_ x] x) "broker.ts"))
    (println "Bootstrapping self-signed certificates...")
    (sun.security.tools.keytool.Main/main (into-array ["-genkeypair" "-alias" "localhost" "-keystore" "selfsigned.ks"
                                                       "-storepass" jms/*key-store-password*
                                                       "-deststoretype" "pkcs12" "-validity" "3650" "-keyalg" "EC"
                                                       "-dname" "CN=localhost" "-ext" "san=ip:127.0.0.1"]))
    (sun.security.tools.keytool.Main/main (into-array ["-export" "-alias" "localhost" "-keystore" "selfsigned.ks"
                                                       "-storepass" jms/*key-store-password* "-rfc" "-file" "selfsigned.pem"]))
    (println "\n\nServer certificate:")
    (-> (slurp "selfsigned.pem") println)
    (sun.security.tools.keytool.Main/main (into-array ["-noprompt" "-importcert" "-trustcacerts" "-file" "selfsigned.pem"
                                                       "-keystore" "selfsigned.ts" "-alias" "localhost_cert"
                                                       "-storepass" jms/*trust-store-password* "-deststoretype" "pkcs12"]))
    (println "Creating client certificate...")
    (sun.security.tools.keytool.Main/main (into-array ["-genkeypair" "-alias" "client" "-keystore" "selfsigned-client.ks"
                                                       "-storepass" "client-password"
                                                       "-deststoretype" "pkcs12" "-validity" "3650" "-keyalg" "EC"
                                                       "-dname" "CN=localhost" "-ext" "san=ip:127.0.0.1"]))
    (println "\n\nClient private key:")
    (try
      (.waitFor (exec-with-out "openssl pkcs12 -in selfsigned-client.ks -nocerts -nodes -passin pass:client-password" println))
      (catch Exception e
        (println "Could not print client private key. Please make sure that OpenSSL is installed.")))
    (sun.security.tools.keytool.Main/main (into-array ["-storepass" "client-password" "-keystore" "selfsigned-client.ks"
                                                       "-certreq" "-alias" "client" "-file" "client-certreq.pem"]))
    (sun.security.tools.keytool.Main/main (into-array ["-keystore" "selfsigned.ks" "-storepass" jms/*key-store-password*
                                                       "-gencert" "-alias" "localhost" "-infile" "client-certreq.pem" "-rfc"
                                                       "-outfile" "client-cert.pem"]))
    (println "\n\nClient certificate:")
    (-> (slurp "client-cert.pem") println)
    (sun.security.tools.keytool.Main/main (into-array ["-noprompt" "-importcert" "-trustcacerts" "-file" "client-cert.pem"
                                                       "-keystore" "selfsigned.ts" "-alias" "client_cert"
                                                       "-storepass" jms/*trust-store-password* "-deststoretype" "pkcs12"]))
    (jio/copy (jio/file "selfsigned.ks") (jio/file jms/*key-store-file*))
    (jio/copy (jio/file "selfsigned.ts") (jio/file jms/*trust-store-file*)))
  (let [arg-url (arg-map :url)
        af-demo-url "ws://127.0.0.1:1864"
        url (if (arg-map :a-frame-demo)
              (if (string? arg-url)
                [arg-url af-demo-url]
                (conj arg-url af-demo-url))
              arg-url)
        broker-service (jms/start-broker url (arg-map :env-vars-for-default-passwords) (cfg "users") (cfg "permissions"))
        producers (atom {})
        running (atom true)
        shutdown-fn (fn []
                      (println "Shutting down...")
                      (reset! running false)
                      (doseq [prod (vals @producers)]
                        (jms/close prod))
                      (sleep 500)
                      (println "Stopping broker...")
                      (jms/stop broker-service)
                      (println "Broker stopped."))]
    (if (arg-map :a-frame-demo)
      (let [af-topic-name "/topic/aframe"
            af-prod (jms/create-json-producer af-demo-url af-topic-name 1)
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
                                (if (> new_angle (- max_angle angle_increment))
                                  (recur (+ 0.0 (- max_angle new_angle)))
                                  (recur new_angle))
                                (do
                                  (println "Stopping A-Frame producer...")
                                  (jms/close af-prod)))))))
          (.setDaemon true)
          (.start))
        (println "To view the example, open the following web page:")
        (println "http://ruedigergad.github.io/bowerick/examples/simple_websocket_a-frame_example/simple_websocket_a-frame_example.html")
        (println "Alternatively, if you have the bowerick source code repository, you can also open the page from the checked out repository:")
        (println "examples/simple_websocket_a-frame_example/simple_websocket_a-frame_example.html")))
    (if (string? (arg-map :embedded-message-generator))
      (let [msg-gen-prod (jms/create-producer
                           (if (string? url)
                             url
                             (first url))
                           (arg-map :destination)
                           (arg-map :pool-size))
            msg-gen-prod-fn (fn [data] (if @running (msg-gen-prod data)))
            msg-delay (arg-map :interval)
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
                 ", sending to:" (arg-map :destination)
                 ", with args:" (arg-map :generator-arguments))
        (doto
          (Thread. #(loop []
                      (msg-gen)
                      (if @running
                        (recur)
                        (do
                          (println "Stopping message-generator producer...")
                          (jms/close msg-gen-prod)))))
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
    (if (arg-map :replay-file)
      (replay (arg-map :replay-file) (arg-map :interval) (arg-map :loop-replay) producers))
    (if (arg-map :daemon)
      (do
        (println "Broker started in daemon mode.")
        (-> (agent 0) (await)))
      (do
        (println "Broker started.\nType \"q\" followed by <Return> to quit: ")
        (while (not= "q" (read-line))
          (println "Type \"q\" followed by <Return> to quit: "))
        (shutdown-fn)))))

(defn start-client-mode
  [arg-map]
  (println-err "Starting bowerick in client mode.")
  (let [json-consumers (atom {})
        json-producers (atom {})
        consumers (atom {})
        out-binding *out*
        producers (atom {})
        recorders (atom {})
        stop-rec-fn (fn [id]
                      (do
                        (println "Stopping recorder for:" id)
                        (doseq [consumer (deref (get-in @recorders [id :consumers]))]
                          (jms/close consumer))
                        (let [f (get-in @recorders [id :file])]
                          (locking f
                            (spit f "\n]}" :append true)))))]
    (start-cli {:cmds
                 {:send {:fn (fn [destination-url data]
                               (create-cached-destination json-producers destination-url jms/create-json-producer)
                               (println "Sending:" destination-url "<-")
                               (pprint data)
                               ((@json-producers destination-url) data))
                         :short-info "Send data to destination."
                         :long-info (str
                                      destination-url-format-help-string)}
                  :s :send
                  :send-file {:fn (fn [destination-url file-name]
                                    (create-cached-destination producers destination-url jms/create-producer)
                                    (println "Sending file:" destination-url "<-" file-name)
                                    (let [ba (Files/readAllBytes (Paths/get file-name (into-array [""])))]
                                      ((@producers destination-url) ba)))
                              :short-info "Send the data from the given file."
                              :long-info (str "Reads binary data from the given file and sends it. "
                                              "For information about the URL format, see the help for \"send\".")}
                  :sf :send-file
                  :send-text-file {:fn (fn [destination-url file-name]
                                         (create-cached-destination producers destination-url jms/create-producer)
                                         (println "Sending text file:" destination-url "<-" file-name)
                                         ((@producers destination-url) (slurp file-name)))
                                   :short-info "Send the content of the given text file."
                                   :long-info (str "Reads the text data from the given file and sends it. "
                                                   "For information about the URL format, see the help for \"send\".")}
                  :stf :send-text-file
                  :set-user-name {:fn (fn [user-name]
                                        (alter-var-root #'bowerick.jms/*user-name* (fn [_ x] (str x)) user-name))
                                  :short-info "Set the user name for user authentication."}
                  :set-user-password {:fn (fn [user-password]
                                            (alter-var-root #'bowerick.jms/*user-password* (fn [_ x] (str x)) user-password))
                                      :short-info "Set the user password for user authentication."}
                  :receive {:fn (fn [destination-url]
                                  (create-cached-destination
                                    json-consumers
                                    destination-url
                                    jms/create-failsafe-json-consumer
                                    (fn [rcvd]
                                      (binding [*out* out-binding]
                                        (with-alt-scroll-out
                                          (println "Received:" destination-url "->")
                                          (pprint rcvd)))))
                                  (println "Set up consumer for:" destination-url))
                         :short-info "Set up a consumer for receiving data from destintation."
                         :long-info (str
                                      destination-url-format-help-string)}
                  :r :receive
                  :record {:fn (fn [record-file-name & source-urls]
                                 (doseq [source-url source-urls]
                                   (let [rec-file (if (contains? @recorders record-file-name)
                                                    (get-in @recorders [record-file-name :file])
                                                    (let [f (jio/file (str record-file-name))]
                                                      (spit f (str
                                                                "{\"metadata\":{\"timestamp_millis\":"
                                                                (System/currentTimeMillis)
                                                                ",\"timestamp_nanos\":"
                                                                (System/nanoTime) "},\n"
                                                                "\"messages\":[\n"))
                                                      f))
                                         rec-consumer (create-cached-destination
                                                        consumers
                                                        source-url
                                                        jms/create-consumer
                                                        (fn [rcvd msg]
                                                          (let [rec-data (String. rcvd)
                                                                rec-itm {"data" rec-data
                                                                         "metadata" {"source" source-url
                                                                                     "timestamp" (System/nanoTime)
                                                                                     "msg-class" (str (class msg))}}]
                                                            (locking rec-file
                                                              (if-not (deref (get-in @recorders [record-file-name :first-message]))
                                                                (spit rec-file ",\n" :append true)
                                                                (reset! (get-in @recorders [record-file-name :first-message]) false))
                                                              (spit rec-file (cheshire/generate-string rec-itm) :append true)))))]
                                     (if (contains? @recorders record-file-name)
                                       (swap! (get-in @recorders [record-file-name :consumers]) conj rec-consumer)
                                       (swap! recorders assoc record-file-name {:consumers (atom [rec-consumer]) :file rec-file :first-message (atom true)}))))
                                 nil)
                           :short-info "Record received data."
                           :long-info (str
                                        "Record data received from source-url in record-file.")}
                  :replay {:fn (fn [replay-file interval loop-send]
                                 (replay replay-file interval loop-send producers))
                          :short-info "Replay previously recorded data."}
                  :stop {:fn (fn [id]
                               (condp contains? id
                                 @recorders (stop-rec-fn id)
                                 @json-consumers (do
                                                   (println "Stopping JSON consumer for:" id)
                                                   (jms/close (@consumers id)))
                                 @consumers (do
                                              (println "Stopping consumer for:" id)
                                              (jms/close (@consumers id)))))
                         :short-info "Stop recording for the given url."}
                  :management {:fn (fn [broker-url command & args]
                                     (create-cached-destination
                                       json-consumers
                                       (str broker-url ":" jms/*broker-management-reply-topic*)
                                       jms/create-failsafe-json-consumer
                                       (fn [reply-str]
                                         (binding [*out* out-binding
                                                   *read-eval* false]
                                           (with-alt-scroll-out
                                             (println "Management Reply:" broker-url "->")
                                             (try
                                               (let [reply-obj (read-string reply-str)]
                                                 (if (instance? clojure.lang.Symbol reply-obj)
                                                   (println reply-str)
                                                   (pprint reply-obj)))
                                               (catch Exception e
                                                 (println "Error reading reply:" (.getMessage e))
                                                 (println "Printing raw reply below:")
                                                 (println reply-str)))))))
                                     (let [cmd-destination (str broker-url ":" jms/*broker-management-command-topic*)
                                           cmd-with-args (str command (reduce #(str %1 " " %2) "" args))]
                                       (create-cached-destination json-producers cmd-destination jms/create-json-producer)
                                       (println "Management Command:" cmd-destination "<-")
                                       (pprint cmd-with-args)
                                       ((@json-producers cmd-destination) cmd-with-args)))}
                  :m :management}
                :prompt-string "bowerick# "
                :alternate-scrolling (not (:old-scroll arg-map (is-os? "Windows")))
                :alternate-height 3})
    (doseq [m [@producers @json-producers @consumers @json-consumers]]
      (doseq [[id consumer] m]
        (println "Closing" (type consumer) "for" id "...")
        (jms/close consumer)))
    (doseq [rec-id (keys @recorders)]
      (stop-rec-fn rec-id))))

(defn start-benchmark-client-mode [arg-map]
  (let [counter (atom 0)
        reception-delay (arg-map :interval)
        consumer-fn (if (and (integer? reception-delay) (pos? reception-delay))
                      (fn [_] (swap! counter inc) (sleep reception-delay))
                      (fn [_] (swap! counter inc)))
        running (atom true)
        stdout *out*
        stderr *err*
        output-thread (doto (Thread. #(loop []
                                        (binding [*out* stdout
                                                  *err* stderr]
                                          (println "Data instances per second:" @counter))
                                        (reset! counter 0)
                                        (sleep 1000)
                                        (if @running
                                          (recur))))
                        (.setDaemon true)
                        (.start))
        url (if (vector? (arg-map :url))
              (first (arg-map :url))
              (arg-map :url))
        consumer (jms/create-consumer url (arg-map :destination) consumer-fn (arg-map :pool-size))
        shutdown-fn (fn []
                      (reset! running false)
                      (jms/close consumer))]
    (println "Benchmark client started... Type \"q\" followed by <Return> to quit: ")
    (while (not= "q" (read-line))
      (println "Type \"q\" followed by <Return> to quit: "))
    (shutdown-fn)))

(defn start-consumer-client-mode [arg-map]
  (let [consumer-arg (arg-map :consumer-client)
        consumer-fn (atom nil)
        read-fn (fn [event]
                  (when (or
                          (nil? event)
                          (and
                            (= 1 (:count event))
                            (= :modify (:action event))
                            (= consumer-arg (-> event :file .getPath))))
                    (println "Loading consumer function:" consumer-arg)
                    (if (s/ends-with? consumer-arg ".class")
                      (let [_ (println "Loading from Java Class file.")
                            consumer-instance (load-and-instantiate-class consumer-arg)
                            cons-fn (fn [data msg-hdr] (.processData consumer-instance data msg-hdr))]
                        (reset! consumer-fn cons-fn))
                      (let [_ (println "Loading from Clojure file.")
                            cons-fn (load-file consumer-arg)]
                        (reset! consumer-fn cons-fn)))))
        _ (if (file-exists? consumer-arg)
            (do
              (read-fn nil)
              (watch-dir read-fn (.getParentFile (clojure.java.io/file consumer-arg))))
            (do
              (println "Reading consumer function from command line argument:" consumer-arg)
              (reset! consumer-fn (-> consumer-arg read-string eval))))
        std-out *out*
        std-err *err*
        consumer-fn-wrapper (fn
                              [data msg-headers]
                              ; Forwarding bindings to consumer function call.
                              ; Reason is that the consumer function is called in another thread
                              ; and thus bindings applied to main would be lost otherwise.
                              (binding [*out* std-out
                                        *err* std-err] 
                                (@consumer-fn data msg-headers)))
        url (if (vector? (arg-map :url))
              (first (arg-map :url))
              (arg-map :url))
        consumer (jms/create-consumer url (arg-map :destination) consumer-fn-wrapper (arg-map :pool-size))
        shutdown-fn (fn []
                      (jms/close consumer))]
    (println "Consumer client started... Type \"q\" followed by <Return> to quit: ")
    (while (not= "q" (read-line))
      (println "Type \"q\" followed by <Return> to quit: "))
    (shutdown-fn)))

(defn print-license-overview []
  (println bowerick-license-text)
  (let [max-name-len (reduce max (map (fn [[[name version] license]] (-> name str count)) license-info))]
    (doseq [[[name-sym version] license] license-info]
      (let [name-str (str name-sym)
            name-len (count name-str)]
        (print name-str)
        (dotimes [_ (- max-name-len name-len)]
          (print " "))
        (println license)))))

(defn run-cli-app [& args]
  (let [cli-args (parse-opts
                   args
                   [["-b" "--bootstrap-self-signed-certs" "In broker mode, bootstrap self-signed certificates."]
                    ["-c" "--client" "Start in interactive client mode."]
                    ["-d" "--daemon" "Run as daemon."]
                    ["-e" "--env-vars-for-default-passwords" "Set default passwords for accessing the broker via the environment variables: BOWERICK_ADMIN_PASS, BOWERICK_WRITE_PASS, BOWERICK_READ_PASS"]
                    ["-f" "--config-file FILE_NAME"
                      "The location of the bowerick configuration file."
                      :default "bowerick.cfg"]
                    ["-h" "--help" "Print this help and exit."]
                    ["-o" "--[no-]old-scroll" "In client mode, use the old scrolling mode of cli4clj."]
                    ["-u" "--url URL"
                      "URL to bind the broker to."
                      :default "tcp://localhost:61616"
                      :parse-fn (fn [arg]
                                  (vec (filter #(not (empty? %)) (s/split (s/replace arg "\"" "") #"[ \[\]]"))))]
                    ["-v" "--verbose"
                      "Increase output verbosity."]
                    ["-A" "--a-frame-demo"
                      "When in broker mode, start a producer for the A-Frame demo."]
                    ["-B" "--benchmark-client"
                     "Start in benchmark client mode."]
                    ["-C" "--consumer-client CONSUMER_FN"
                      "Start as consumer client. CONSUMER_FN specifies the consumer function either as string or as path to a file from which the consumer function is read."]
                    ["-D" "--destination DESTINATION"
                      "The destination to which message generators will send messages or from which the benchmark client retrieves messages."
                      :default "/topic/bowerick.message.generator"]
                    ["-G" "--embedded-message-generator GEN_NAME"
                      "When in broker mode, start the specified embedded message generator."]
                    ["-I" "--interval INTERVAL"
                      (str
                        "The (rough) interval in milliseconds with which, e.g., messages shall be generated by the message generator producer"
                        " or the delay that is artificially added to the consumption fn of the benchmark client.")
                      :default 0
                      :parse-fn #(Integer/decode %)]
                    ["-L" "--[no-]loop-replay"
                      "Whether or not to loop the replayed file."
                      :default true]
                    ["-P" "--pool-size SIZE"
                      "The pool size used, e.g., for the benchmark client or the message generator producer."
                      :default 1
                      :parse-fn #(Integer/decode %)]
                    ["-R" "--replay-file FILE_NAME"
                      "Replay a recorded file."]
                    ["-X" "--generator-arguments GEN_ARGS"
                      "Arguments for message generators that accept arguments."]
                    [nil "--license-information" "Print information about the licenses of bowerick and its dependencies."]
                    [nil "--license-information-long" "Print information about the licenses of bowerick and its dependencies including the license full texts."]])
        arg-map (cli-args :options)
        extra-args (cli-args :arguments)
        help-string (cli-args :summary)]
    (cond
      (arg-map :help)
        (do
          (println "Bowerick help:")
          (println help-string))
      (arg-map :license-information) (print-license-overview)
      (arg-map :license-information-long)
        (do
          (print-license-overview)
          (println "\n\n")
          (doseq [f license-text-files]
            (dotimes [_ (count f)] (print "#"))
            (println "")
            (println f)
            (dotimes [_ (count f)] (print "#"))
            (println "\n\n")
            (println (->> (jio/resource (str "license_texts/" f)) slurp))))
      :default
        (let [cfg-data (binding [*out* *err*]
                         (println "Starting bowerick using the following options:")
                         (pprint arg-map)
                         (pprint extra-args)
                         (let [cfg-file-path (arg-map :config-file)
                               cfg-file-data (if (is-file? cfg-file-path)
                                               (cheshire/parse-string (slurp cfg-file-path))
                                               {})
                               jms-cfg (cfg-file-data "jms")]
                           (when (not (nil? jms-cfg))
                             (println "Setting JMS configuration...")
                             (doseq [[k v] jms-cfg]
                               (when (arg-map :verbose)
                                 (println "Setting" k ":" v))
                               (alter-var-root (ns-resolve 'bowerick.jms (symbol k)) (fn [_ x] x) v)))
                           cfg-file-data))
             cfg-with-default-auth (if (arg-map :env-vars-for-default-passwords)
                                     (let [admin-password (System/getenv "BOWERICK_ADMIN_PASS")
                                           write-password (System/getenv "BOWERICK_WRITE_PASS")
                                           read-password (System/getenv "BOWERICK_READ_PASS")
                                           _ (if (or (nil? admin-password)
                                                     (nil? write-password)
                                                     (nil? read-password))
                                               (throw (Exception. "Error: specified to set passwords from environment variables but not all environment variables are set.")))
                                           users [{"name" "admin", "password" admin-password, "groups" "admin-group"}
                                                  {"name" "write", "password" write-password, "groups" "write-group"}
                                                  {"name" "read", "password" read-password, "groups" "read-group"}]
                                           permissions [{"target" "/topic/>", "admin" "admin-group", "write" "admin-group,write-group", "read" "admin-group,write-group,read-group"}
                                                        {"target" "/queue/>", "admin" "admin-group", "write" "admin-group,write-group", "read" "admin-group,write-group,read-group"}]
                                           cfg-with-users (if (contains? cfg-data "users")
                                                            (update cfg-data "users" conj users)
                                                            (assoc cfg-data "users" users))
                                           cfg-with-permissions (if (contains? cfg-with-users "permissions")
                                                                  (update cfg-with-users "permissions" conj permissions)
                                                                  (assoc cfg-with-users "permissions" permissions))]
                                       (alter-var-root #'bowerick.jms/*user-name* (fn [_ x] x) "admin")
                                       (alter-var-root #'bowerick.jms/*user-password* (fn [_ x] x) admin-password)
                                       cfg-with-permissions)
                                     cfg-data)]
          (cond
            (arg-map :client) (start-client-mode arg-map)
            (arg-map :benchmark-client) (start-benchmark-client-mode arg-map)
            (arg-map :consumer-client) (start-consumer-client-mode arg-map)
            :default (start-broker-mode arg-map cfg-with-default-auth))))))

(defn -main [& args]
  (apply run-cli-app args)
  (run-once
    (executor)
    #((println "Terminating...")
      (System/exit 0))
    2000))

