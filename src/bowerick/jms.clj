;;;
;;;   Copyright 2016, Ruediger Gad
;;;   Copyright 2015, Frankfurt University of Applied Sciences
;;;
;;;   This software is released under the terms of the Eclipse Public License 
;;;   (EPL) 1.0. You can find a copy of the EPL at: 
;;;   http://opensource.org/licenses/eclipse-1.0.php
;;;

(ns
  ^{:author "Ruediger Gad",
    :doc "Functions for JMS interaction"} 
  bowerick.jms
  (:require
    [carbonite.api :as carb-api]
    [carbonite.buffer :as carb-buf]
    [cheshire.core :as cheshire]
    [cli4clj.cli :as cli]
    [clojure.java.io :as java-io]
    [clojure.string :as str]
    [clj-assorted-utils.util :as utils]
    [taoensso.nippy :as nippy])
  (:import
    (bowerick JmsProducer)
    (clojure.lang IFn)
    (com.ning.compress.lzf LZFDecoder LZFEncoder)
    (java.lang AutoCloseable)
    (java.nio.charset Charset)
    (java.security KeyStore)
    (java.util ArrayList List)
    (java.util.concurrent ScheduledThreadPoolExecutor ThreadFactory)
    (java.util.concurrent.locks ReentrantLock)
    (javax.jms BytesMessage Connection ConnectionFactory DeliveryMode Message MessageProducer MessageListener ObjectMessage Session TextMessage Topic)
    (javax.net.ssl KeyManagerFactory SSLContext TrustManagerFactory)
    (org.apache.activemq ActiveMQConnectionFactory ActiveMQMessageConsumer ActiveMQSslConnectionFactory)
    (org.apache.activemq.broker BrokerService SslBrokerService SslContext)
    (org.apache.activemq.broker.region Destination)
    (org.apache.activemq.broker.region.policy PolicyEntry PolicyMap)
    (org.apache.activemq.security AuthenticationUser AuthorizationEntry AuthorizationMap AuthorizationPlugin DefaultAuthorizationMap SimpleAuthenticationPlugin)
    (org.eclipse.jetty.client HttpClient)
    (org.eclipse.jetty.util.ssl SslContextFactory)
    (org.eclipse.jetty.util.thread ScheduledExecutorScheduler)
    (org.eclipse.jetty.websocket.client WebSocketClient)
    (org.eclipse.paho.client.mqttv3 MqttCallback MqttClient MqttConnectOptions MqttMessage)
    (org.eclipse.paho.client.mqttv3.persist MemoryPersistence)
    (org.fusesource.stomp.jms StompJmsConnectionFactory)
    (org.iq80.snappy Snappy)
    (org.springframework.messaging.converter ByteArrayMessageConverter SmartMessageConverter StringMessageConverter)
    (org.springframework.messaging.simp.stomp DefaultStompSession StompFrameHandler StompHeaders StompSession StompSessionHandler StompSessionHandlerAdapter)
    (org.springframework.scheduling.concurrent DefaultManagedTaskScheduler ThreadPoolTaskScheduler)
    (org.springframework.web.socket.messaging WebSocketStompClient)
    (org.springframework.web.socket.client.jetty JettyWebSocketClient)))

(def ^:dynamic *user-name* nil)
(def ^:dynamic *user-password* nil)

(def ^:dynamic *trust-store-file* "client.ts")
(def ^:dynamic *trust-store-password* "password")
(def ^:dynamic *key-store-file* "client.ks")
(def ^:dynamic *key-store-password* "password")

(def ^:dynamic *broker-management-command-topic* "/topic/bowerick.broker.management.command")
(def ^:dynamic *broker-management-reply-topic* "/topic/bowerick.broker.management.reply")

(def ^sun.nio.cs.Unicode ^:dynamic *default-charset* (Charset/forName "UTF-8"))

(def ^:dynamic *ws-client-ping-heartbeat* 10000)
(def ^:dynamic *ws-client-pong-heartbeat* 10000)

(def ^:dynamic *force-sync-send* true)
; Producer window size is in bytes.
(def ^:dynamic *producer-window-size* (* 10 1024 1024))

(def ^:dynamic *pooled-producer-auto-transmit-interval* 100)

(def ^:dynamic *mqtt-max-in-flight* (MqttConnectOptions/MAX_INFLIGHT_DEFAULT))

(def msg-prop-key :message-properties)

; See also: http://activemq.apache.org/objectmessage.html
(def ^:dynamic *serializable-packages*
  '("clojure.lang"
    "java.lang"
    "java.math"
    "java.util"
    "org.apache.activemq"
    "org.fusesource.hawtbuf"
    "com.thoughtworks.xstream.mapper"))

(def ^:dynamic *serializable-allowlist*
  #{"clojure.lang.*"
    "java.lang.*"
    "java.math.*"
    "java.util.*"
    "org.apache.activemq.*"
    "org.fusesource.hawtbuf.*"
    "com.thoughtworks.xstream.mapper.*"})

(defn get-adjusted-ssl-context
  "Get an SSLContext for which the key and trust stores are initialized based
   on the settings defined in the global dynamic vars:
   *key-store-file* *key-store-password*
   *trust-store-file* *trust-store-password*"
  []
  (let [keyManagerFactory (doto
                            (KeyManagerFactory/getInstance "SunX509")
                            (.init
                              (doto
                                (KeyStore/getInstance "JKS")
                                (.load
                                  (java-io/input-stream *key-store-file*)
                                  (char-array *key-store-password*)))
                              (char-array *key-store-password*)))
        trustManagerFactory (doto
                              (TrustManagerFactory/getInstance "SunX509")
                              (.init
                                (doto (KeyStore/getInstance "JKS")
                                  (.load
                                    (java-io/input-stream *trust-store-file*)
                                    (char-array *trust-store-password*)))))]
    (doto
      (SSLContext/getInstance "TLS")
      (.init
        (.getKeyManagers keyManagerFactory)
        (.getTrustManagers trustManagerFactory)
        nil))))

(defn get-destinations
  "Get a lexicographically sorted list of destinations that exist for the given borker-service
   Optionally, destinations without producers can be excluded by setting
   include-destinations-without-producers to false."
  ([broker]
    (get-destinations broker true))
  ([broker include-destinations-without-producers]
    (let [^BrokerService broker-service (if
                                          (map? broker)
                                          (:broker broker)
                                          broker)
          dst-vector (ref [])]
      (doseq [^Destination dst (vec
                                 (->
                                   (.getBroker broker-service)
                                   (.getDestinationMap)
                                   (.values)))]
        (if (or
              include-destinations-without-producers
              (>
                (-> (.getDestinationStatistics dst) (.getProducers) (.getCount))
                0))
          (let [dst-type (condp (fn[t d] (= (type d) t)) dst
                           org.apache.activemq.broker.region.Topic "/topic/"
                           org.apache.activemq.broker.region.Queue "/queue/"
                           "/na/")]
            (dosync
              (alter dst-vector conj (str dst-type (.getName dst)))))))
      (sort @dst-vector))))

(defn send-error-msg [producer msg]
  (utils/println-err msg)
  (producer (str "error " msg)))

(defn setup-broker-with-auth
  [allow-anon users permissions]
  (let [user-list (map
                    (fn [u]
                      (AuthenticationUser.
                        (u "name")
                        (u "password")
                        (u "groups")))
                    users)
        authentication-plugin (doto
                                (SimpleAuthenticationPlugin. user-list)
                                (.setAnonymousAccessAllowed allow-anon)
                                (.setAnonymousUser "anonymous")
                                (.setAnonymousGroup "anonymous"))
        authorization-entries (map
                                (fn [perm]
                                  (utils/println-err "Setting permission:" perm)
                                  (let [split-trgt (filter #(not= % "") (str/split (perm "target") #"/"))
                                        trgt-type (first split-trgt)
                                        trgt (second split-trgt)
                                        adm (perm "admin")
                                        rd (perm "read")
                                        wrt (perm "write")
                                        auth-entry (AuthorizationEntry.)]
                                    (if (not (nil? adm))
                                      (.setAdmin auth-entry adm))
                                    (if (not (nil? rd))
                                      (.setRead auth-entry rd))
                                    (if (not (nil? wrt))
                                      (.setWrite auth-entry wrt))
                                    (condp = trgt-type
                                      "topic" (.setTopic auth-entry trgt)
                                      "queue" (.setQueue auth-entry trgt)
                                      (.setDestination auth-entry trgt))
                                    auth-entry))
                                permissions)
        authorization-map (doto
                            (DefaultAuthorizationMap.)
                            (.setAuthorizationEntries authorization-entries))
        authorization-plugin (AuthorizationPlugin. authorization-map)
        broker (doto
                 (BrokerService.)
                 (.setPlugins
                   (into-array
                     org.apache.activemq.broker.BrokerPlugin
                     [authentication-plugin authorization-plugin])))]
    broker))

(declare create-json-producer)
(declare create-json-consumer)
(declare close)

(defn start-broker
  "Start an embedded ActiveMQ broker.
   Examples for valid addresses are: 
  
   ssl://127.0.0.1:424
   
   ssl://127.0.0.1:42425?needClientAuth=true
   
   stomp+ssl://127.0.0.1:42423
   
   stomp+ssl://127.0.0.1:42423?needClientAuth=true
   
   wss://127.0.0.1:42427
   
   wss://127.0.0.1:42427?needClientAuth=true
   
   mqtt+ssl://127.0.0.1:42429
   
   mqtt+ssl://127.0.0.1:42429?needClientAuth=true
   
   tcp://127.0.0.1:42424
   
   udp://127.0.0.1:42426
   
   ws://127.0.0.1:42428
   
   mqtt://127.0.0.1:42430
   
   In addition to the address, it is possible to configure the access control:
   When allow-anon is true, anonymous access is allowed.
   The list of users is defined as a vector of maps, e.g.:
   
   [{\"name\" \"test-user\", \"password\" \"secret\", \"groups\" \"test-group,admins,publishers,consumers\"}]
   
   Permissions are also defined as a vector of maps, e.g.:
   
   [{\"target\" \"test.topic.a\", \"type\" \"topic\", \"write\" \"anonymous\"}"
  ([address]
    (start-broker address nil nil nil))
  ([address allow-anon users permissions]
    (let [^BrokerService broker (if (and allow-anon users permissions)
                                  (setup-broker-with-auth allow-anon users permissions)
                                  (BrokerService.))
          _ (doto broker
              (.setPersistent false)
              (.setUseJmx false)
              (.setStartAsync false)
              (.setDestinationPolicy
                (doto (PolicyMap.)
                  (.setDefaultEntry
                    (doto (PolicyEntry.)
                      (.setProducerFlowControl *force-sync-send*)
                      (.setMemoryLimit *producer-window-size*))))))
          _ (when
              (and
                (utils/file-exists? *key-store-file*)
                (utils/file-exists? *trust-store-file*))
              (utils/println-err
                "Setting broker SSLContext to use key store file:"
                *key-store-file*
                "and trust store file:"
                *trust-store-file*)
              (.setSslContext
                ^SslBrokerService broker
                (doto
                  (SslContext.)
                  (.setSSLContext
                    (get-adjusted-ssl-context)))))
          _ (if (string? address)
              (.addConnector broker ^String address)
              (doseq [^String addr (seq address)]
                (.addConnector broker addr)))
          _ (.start broker)
          _ (.waitUntilStarted broker)
          management-address (cond
                               (sequential? address) (first address)
                               :default address)
          producer (try
                     (utils/println-err "Info: Enabling management producer at:" *broker-management-reply-topic*)
                     (binding [*trust-store-file* *key-store-file*
                               *trust-store-password* *key-store-password*
                               *key-store-file* *trust-store-file*
                               *key-store-password* *trust-store-password*]
                       (create-json-producer
                         management-address
                         *broker-management-reply-topic*))
                     (catch Exception e
                       (utils/println-err "Warning: Could not create management producer for:" *broker-management-reply-topic*)))
          mgmt-cli (cli/embedded-cli-fn {:cmds {:get-destinations {:fn (fn [] (get-destinations broker false))
                                                                   :short-info "List topics with producers."
                                                                   :long-info "Get a list of all topics for which producers are registered."}
                                                :ls :get-destinations
                                                :get-all-destinations {:fn (fn [] (get-destinations broker true))
                                                                       :short-info "List all topics."
                                                                       :long-info "Get a list of all topics, even those without producers."}
                                                :la :get-all-destinations}})
          consumer (try
                     (utils/println-err "Info: Enabling management consumer at:" *broker-management-command-topic*)
                     (binding [*trust-store-file* *key-store-file*
                               *trust-store-password* *key-store-password*
                               *key-store-file* *trust-store-file*
                               *key-store-password* *trust-store-password*]
                       (create-json-consumer
                         management-address
                         *broker-management-command-topic*
                         (fn [cmd]
                           (try
                             (let [ret (mgmt-cli cmd)]
                               (producer ret))
                             (catch Exception e
                               (utils/println-err "Error while executing management command:" cmd)
                               (utils/println-err (str e)))))))
                     (catch Exception e
                       (utils/println-err "Warning: Could not create management consumer for:" *broker-management-command-topic*)))]8
      {:broker broker
       :stop (fn []
               (if consumer
                 (close consumer))
               (if producer
                 (close producer))
               (.stop broker)
               (.waitUntilStopped broker)
               (utils/sleep 100))})))

(defn stop
  [brkr]
  ((:stop brkr)))

(defn remove-url-options
  [^String url]
  (if
    (.contains url "?")
    (.substring url 0 (.indexOf url "?"))
    url))

(defn fallback-serialization
  [data]
    (condp instance? data
      utils/byte-array-type data
      String (.getBytes ^String data *default-charset*)
      (.getBytes
        ^String (cheshire/generate-string data)
        *default-charset*)))

(defn create-mqtt-client
  [^String broker-url]
  (let [^String url (-> broker-url
                      (.replaceFirst "mqtt\\+ssl://" "ssl://")
                      (.replaceFirst "mqtt://" "tcp://")
                      (remove-url-options))
        _ (println "Adjusted MQTT URL from" broker-url "to" url)
        mqtt-client (MqttClient. url (MqttClient/generateClientId) nil)
        conn-opts (doto (MqttConnectOptions.)
                    (.setCleanSession true)
                    (.setMaxInflight *mqtt-max-in-flight*)
                    (.setConnectionTimeout 180)
                    (.setKeepAliveInterval 60))]
    (when (.startsWith url "ssl://")
      (utils/println-err "Setting socket factory for SSL connection, trust store:" *trust-store-file* "; key store:" *key-store-file*)
      (.setSocketFactory conn-opts
        (.getSocketFactory ^SSLContext (get-adjusted-ssl-context))))
    (.connect mqtt-client conn-opts)
    mqtt-client))

(def ws-scheduler-id (ref 0))

(defn create-ws-stomp-session
  [^String broker-url]
  (let [sched-id (dosync
                   (let [current-value @ws-scheduler-id]
                     (alter ws-scheduler-id inc)
                     current-value))
        stp-exec (ScheduledThreadPoolExecutor.
                   10
                   (proxy [ThreadFactory] []
                     (newThread [^Runnable r]
                       (doto (Thread. r)
                         (.setDaemon true)))))
        se-sched (doto
                   (ScheduledExecutorScheduler.
                     (str "HttpClient-Scheduler-" broker-url "-" sched-id) true)
                   (.start))
        ws-client (WebSocketClient.
                    (doto
                      (if (.startsWith broker-url "wss://")
                        (HttpClient.
                          (doto
                            (SslContextFactory.)
                            (.setSslContext
                              (get-adjusted-ssl-context))))
                        (HttpClient.))
                      (.setExecutor stp-exec)
                      (.setScheduler se-sched)
                      (.start)))
        jws-client (doto
                    (JettyWebSocketClient. ws-client)
                    (.start))
        ws-stomp-client (doto
                          (WebSocketStompClient. jws-client)
                          (.setTaskScheduler (DefaultManagedTaskScheduler.))
                          (.setDefaultHeartbeat (long-array [*ws-client-ping-heartbeat* *ws-client-pong-heartbeat*])))
        session (atom nil)
        flag (utils/prepare-flag)]
    (println "Connecting WS STOMP client...")
    (.connect
      ws-stomp-client
      broker-url
      (proxy [StompSessionHandlerAdapter] []
        (afterConnected [^StompSession new-session ^StompHeaders stomp-headers]
          (reset! session new-session)
          (utils/set-flag flag)))
      (object-array 0))
    (println "Waiting for WS STOMP client to connect...")
    (utils/await-flag flag)
    (println "WS STOMP client connection succeeded.")
    {:ws-client ws-client
     :jws-client jws-client
     :ws-stomp-client ws-stomp-client
     :session @session
     :stp-exec stp-exec
     :se-sched se-sched}))

(defn close-ws-stomp-session
  [session-map]
  (println "Closing WebSocket session...")
  (.disconnect ^StompSession (:session session-map))
  (.stop ^WebSocketStompClient (:ws-stomp-client session-map))
  (.stop ^JettyWebSocketClient (:jws-client session-map))
  (doto ^WebSocketClient (:ws-client session-map) .stop .destroy)
  (.shutdownNow ^java.util.concurrent.ExecutorService (:stp-exec session-map))
  (.stop ^ScheduledExecutorScheduler (:se-sched session-map)))

(defmacro with-destination
  "Execute body in a context for which connection, session, and destination are
   made available based on the provided broker-url and destination-description.
   
   The broker-url is the address of the broker to which a connection shall
   be establishd. Examples for valid address schemes are given in the doc
   string of start-broker.
  
   Destination descriptions have the form \"/<type>/<name>\" for which \"<type>\"
   is currently either \"topic\" or \"queue\" and the \"<name>\" is the unique
   name of the destination."
  [broker-url destination-description & body]
  `(let [^ConnectionFactory factory# (cond
                                       (or (.startsWith ~broker-url "ssl:")
                                           (.startsWith ~broker-url "tls:"))
                                         (doto
                                           (ActiveMQSslConnectionFactory.
                                             ^String (remove-url-options ~broker-url))
                                           (.setTrustStore *trust-store-file*) (.setTrustStorePassword *trust-store-password*)
                                           (.setKeyStore *key-store-file*) (.setKeyStorePassword *key-store-password*)
                                           (.setTrustedPackages *serializable-packages*))
                                       (.startsWith ~broker-url "stomp:")
                                         (doto
                                           (StompJmsConnectionFactory.)
                                           (.setBrokerURI (.replaceFirst ~broker-url "stomp" "tcp")))
                                       (.startsWith ~broker-url "stomp+ssl:")
                                         (doto
                                           (StompJmsConnectionFactory.)
                                           (.setSslContext (get-adjusted-ssl-context))
                                           (.setBrokerURI (.replaceFirst ~broker-url "stomp\\+ssl" "ssl")))
                                       :default (doto
                                                  (ActiveMQConnectionFactory. ~broker-url)
                                                  (.setTrustedPackages *serializable-packages*)))
         ~'connection (doto
                        (if (and (not (nil? *user-name*)) (not (nil? *user-password*)))
                          (do
                            (utils/println-err "Creating connection for user:" *user-name*)
                            (.createConnection factory# *user-name* *user-password*))
                          (do
                            (utils/println-err "Creating connection.")
                            (.createConnection factory#)))
                        (.start))
         ~'session ~(with-meta
                      `(.createSession ~'connection false Session/AUTO_ACKNOWLEDGE)
                      {:tag 'javax.jms.Session})
         split-destination# (filter #(not= % "") (str/split ~destination-description #"/"))
         destination-type# (first split-destination#)
         destination-name# (str/join "/" (rest split-destination#))
         _# (utils/println-err "Creating destination. Type:" destination-type# "Name:" destination-name#)
         ~'destination (condp = destination-type#
                      "topic" (.createTopic ~'session destination-name#)
                      "queue" (.createQueue ~'session destination-name#)
                      (utils/println-err "Could not create destination. Type:" destination-type# "Name:" destination-name#))]
     ~@body))

(defrecord ProducerWrapper [send-fn send-fn-opt-args close-fn]
  AutoCloseable
    (close [this]
      (close-fn))
  JmsProducer
    (sendData [this data]
      (send-fn data))
    (sendData [this data opt-args]
      (send-fn-opt-args data opt-args))
  IFn
    (invoke [this data]
      (send-fn data))
    (invoke [this data opt-args]
      (send-fn-opt-args data opt-args)))

(defn create-single-producer
  "Create a message producer for sending data to the specified destination and server/broker.

   The created producer implements IFn. Hence, the idiomatic way for using it in Clojure
   is to use the producer as a function to which the data that is to be transmitted is
   passed as single function argument.
  
   For each invocation, the passed data will be transmitted in a separate message.
   
   Optionally, a single argument function for customizing the serialization of the data can be given.
   This defaults to idenitity such that the default serialization of the underlying JMS implementation is used.
  
   This function is not intended to be used directly.
   It is recommended to use create-producer or the various create-XXXXXX-producer derivatives that employ
   customized serialization mechanisms instead."
  ([broker-url destination-description]
    (create-single-producer broker-url destination-description identity))
  ([^String broker-url ^String destination-description serialization-fn]
    (utils/println-err "Creating producer:" broker-url destination-description)
    (cond
      (.startsWith
        broker-url
        "ws") (let [session-map (create-ws-stomp-session broker-url)
                    session ^StompSession (:session session-map)
                    send-fn (fn [data]
                              (let [serialized-data (serialization-fn data)
                                    byte-array-data (fallback-serialization serialized-data)
                                    stomp-headers (doto
                                                    (StompHeaders.)
                                                    (.setDestination ^String destination-description))]
                                (.send session stomp-headers byte-array-data)))
                    send-fn-opt-args (fn [data _]
                                       (utils/println-err "Sending with opt-args is not supported for ws://. Ignoring opt-args.")
                                       (send-fn data))]
                (->ProducerWrapper
                  send-fn
                  send-fn-opt-args
                  (fn []
                    (utils/println-err "Closing producer:" broker-url destination-description)
                    (close-ws-stomp-session session-map))))
      (.startsWith
        broker-url
        "mqtt") (let [^MqttClient mqtt-client (create-mqtt-client broker-url)
                      dst-descrpt (->
                                    destination-description
                                    (.replaceFirst "(/)(topic|queue)(/)" "")
                                    (.replace "." "/"))
                      send-fn (fn [data]
                                (.publish
                                  mqtt-client
                                  dst-descrpt
                                  (MqttMessage.
                                    ^bytes (-> data serialization-fn fallback-serialization))))
                      send-fn-opt-args (fn [data _]
                                         (utils/println-err "Sending with opt-args is not supported for mqtt://. Ignoring opt-args.")
                                         (send-fn data))]
                  (->ProducerWrapper
                    send-fn
                    send-fn-opt-args
                    (fn []
                      (utils/println-err "Closing producer:" broker-url destination-description)
                      (.disconnect mqtt-client)
                      (.close mqtt-client))))
      :default (with-destination broker-url destination-description
                 (let [producer (doto
                                  (.createProducer session destination)
                                  (.setDeliveryMode DeliveryMode/NON_PERSISTENT))
                       create-msg (fn [serialized-data]
                                    (condp instance? serialized-data
                                      utils/byte-array-type (doto
                                                              (.createBytesMessage session)
                                                              (.writeBytes ^bytes serialized-data))
                                      String (doto
                                               (.createTextMessage session ^String serialized-data)
                                               (.setStringProperty "transformation" "TEXT"))
                                      (.createObjectMessage session serialized-data)))
                       send-fn (fn [data]
                                 (let [serialized-data (serialization-fn data)
                                       ^Message msg (create-msg serialized-data)]
                                   (.send producer msg)))
                       send-fn-opt-args (fn [data opt-args]
                                          (let [serialized-data (serialization-fn data)
                                                ^Message msg (create-msg serialized-data)
                                                msg-properties (msg-prop-key opt-args)]
                                            (doseq [[^String k v] msg-properties]
                                              (condp instance? v
                                                Boolean (.setBooleanProperty msg k v)
                                                Byte (.setByteProperty msg k v)
                                                Double (.setDoubleProperty msg k v)
                                                Float (.setFloatProperty msg k v)
                                                Integer (.setIntProperty msg k v)
                                                Long (.setLongProperty msg k v)
                                                Short (.setShortProperty msg k v)
                                                String (.setStringProperty msg k v)
                                                (.setObjectProperty msg k v)))
                                            (.send producer msg)))]
                   (->ProducerWrapper
                     send-fn
                     send-fn-opt-args
                     (fn []
                       (utils/println-err "Closing producer:" broker-url destination-description)
                       (.close connection))))))))

(defrecord ConsumerWrapper [close-fn]
  AutoCloseable
    (close [this]
      (try
        (close-fn)
        (catch Exception e
          (println "Caught exception while closing consumer.")
          (.printStackTrace e)))))

(defn create-single-consumer
  "Create a message consumer for receiving data from the specified destination and server/broker.

   The passed callback function (cb) will be called for each message and will receive the data
   from the message as its single argument.
  
   Optionally, a single argument function for customizing the de-serialization of the transferred data can be given.
   Typically, this should be the inverse operation of the serialization function as used for the producer and defaults to identity.
  
   This function is not intended to be used directly.
   It is recommended to use create-consumer or the various create-XXXXXX-consumer derivatives that employ
   customized serialization mechanisms instead."
  ([broker-url destination-description cb]
    (create-single-consumer broker-url destination-description cb identity))
  ([^String broker-url ^String destination-description cb de-serialization-fn]
    (utils/println-err "Creating consumer:" broker-url destination-description)
    (let [cb-args-count (-> cb class .getDeclaredMethods ^java.lang.reflect.Method first .getParameterTypes count)
          internal-cb (condp = cb-args-count
                        1 (fn [data _]
                            (cb data))
                        2 (fn [data hdrs]
                            (cb data hdrs))
                        (utils/println-err "Invalid callback args count:" cb-args-count))]
      (utils/println-err "Consumer callback args count:" cb-args-count)
      (cond
        (.startsWith
          broker-url
          "ws") (let [session-map (create-ws-stomp-session broker-url)]
                  (println "Subscribing to" destination-description)
                  (.subscribe
                    ^StompSession (:session session-map)
                    destination-description
                    (proxy [StompFrameHandler] []
                      (getPayloadType [^StompHeaders stomp-headers]
                        java.lang.Object)
                      (handleFrame [^StompHeaders stomp-headers payload]
                        (try
                          (internal-cb (de-serialization-fn payload) stomp-headers)
                          (catch Exception e
                            (utils/println-err e))))))
                  (println "Subscription succeeded.")
                  (->ConsumerWrapper
                    (fn []
                      (utils/println-err "Closing consumer:" broker-url destination-description)
                      (close-ws-stomp-session session-map))))
        (.startsWith
          broker-url
          "mqtt") (let [^MqttClient mqtt-client (create-mqtt-client broker-url)
                        dst-descrpt (->
                                      destination-description
                                      (.replaceFirst "(/)(topic|queue)(/)" "")
                                      (.replace "." "/"))]
                    (.setCallback
                      mqtt-client
                      (proxy [MqttCallback] []
                        (connectionLost [cause]
                          (utils/println-err "Connection lost (" broker-url dst-descrpt "):" cause))
                        (deliveryComplete [token]
                          (utils/println-err "Delivery complete (" broker-url dst-descrpt "):" token))
                        (messageArrived [^String topic ^MqttMessage message]
                          (try
                            (internal-cb (de-serialization-fn (.getPayload message)) message)
                            (catch Exception e
                              (utils/println-err e))))))
                    (.subscribe mqtt-client dst-descrpt)
                    (->ConsumerWrapper
                      (fn []
                        (utils/println-err "Closing consumer:" broker-url destination-description)
                        (.disconnect mqtt-client)
                        (.close mqtt-client))))
        :default (with-destination broker-url destination-description
                   (let [consumer (.createConsumer session destination)
                         listener (proxy [MessageListener] []
                                    (onMessage [^Message m]
                                      (try
                                        (condp instance? m
                                          BytesMessage (let [data (byte-array (.getBodyLength ^BytesMessage m))]
                                                         (.readBytes ^BytesMessage m data)
                                                         (internal-cb (de-serialization-fn data) m))
                                          ObjectMessage  (internal-cb (de-serialization-fn (.getObject ^ObjectMessage m)) m)
                                          TextMessage (internal-cb (de-serialization-fn (.getText ^TextMessage m)) m)
                                          (utils/println-err "Unknown message type (" broker-url destination-description "):" (type m)))
                                        (catch Exception e
                                          (utils/println-err e)))))]
                     (.setMessageListener consumer listener)
                     (->ConsumerWrapper
                       (fn []
                         (utils/println-err "Closing consumer:" broker-url destination-description)
                         (.close connection)))))))))

(defn close
  "Close a producer or consumer."
  [^AutoCloseable o]
  (.close o))

(defn create-pooled-producer
  "Create a pooled producer with the given pool-size for the given broker-url and destination-description.
  
   A pooled producer does not send the passed data instances individually but groups them in a pool and
   sends the entire pool at once when the pool is filled.
  
   Optionally, a single argument function for customizing the serialization of the pooled-data can be given.
   This defaults to identity such that the default serialization of the underlying JMS implementation is used.
  
   This function is not intended to be used directly.
   It is recommended to use create-producer or the various create-XXXXXX-producer derivatives that employ
   customized serialization mechanisms instead."
  ([broker-url destination-description pool-size]
    (create-pooled-producer broker-url destination-description pool-size identity))
  ([broker-url destination-description ^long pool-size serialization-fn]
    (utils/println-err "Creating pooled producer:" broker-url destination-description pool-size)
    (let [^AutoCloseable producer (create-single-producer broker-url destination-description serialization-fn)
          pool (ArrayList. pool-size)
          lock (ReentrantLock.)
          opt-args (atom nil)
          last-sent (atom (System/nanoTime))
          auto-transmit-fn (fn []
                             (when (>
                                    (-> (System/nanoTime) (- @last-sent) (/ 1000000))
                                    *pooled-producer-auto-transmit-interval*)
                               (.lock lock)
                               (try
                                 (when (not (.isEmpty pool))
                                   (if (not (nil? @opt-args))
                                     (producer pool @opt-args)
                                     (producer pool))
                                   (.clear pool)
                                   (reset! opt-args nil)
                                   (reset! last-sent (System/nanoTime)))
                                 (finally
                                   (.unlock lock)))))
          ^java.util.concurrent.ExecutorService exec (utils/executor)]
      (utils/run-repeat exec auto-transmit-fn *pooled-producer-auto-transmit-interval*)
      (->ProducerWrapper
        (fn [d]
          (.lock lock)
          (try
            (.add pool d)
            (if (not (nil? @opt-args))
              (reset! opt-args nil))
            (when (>= (.size pool) pool-size)
              (producer pool)
              (.clear pool)
              (reset! last-sent (System/nanoTime)))
            (finally
              (.unlock lock))))
        (fn [d oa]
          (.lock lock)
          (try
            (.add pool d)
            (reset! opt-args oa)
            (when (>= (.size pool) pool-size)
              (producer pool opt-args)
              (.clear pool)
              (reset! opt-args nil)
              (reset! last-sent (System/nanoTime)))
            (finally
              (.unlock lock))))
        (fn []
          (utils/println-err "Closing pooled producer.")
          (.shutdownNow exec)
          (.close producer))))))

(defn create-pooled-consumer
  "Create a consumer for receiving pooled data.
   
   The callback function, cb, will be called for each data instance in the pool individually.
  
   Optionally, a single argument function for customizing the de-serialization of the transferred data can be given.
   Typically, this should be the inverse operation of the serialization function as used for the pooled producer and defaults to identity.
  
   This function is not intended to be used directly.
   It is recommended to use create-consumer or the various create-XXXXXX-consumer derivatives that employ
   customized serialization mechanisms instead."
  ([broker-url destination-description cb]
    (create-pooled-consumer broker-url destination-description cb identity))
  ([broker-url destination-description cb de-serialization-fn]
    (utils/println-err "Creating pooled consumer:" broker-url destination-description)
    (let [pooled-cb (fn [^List lst]
                      (doseq [o lst]
                        (cb o)))
          consumer (create-single-consumer broker-url destination-description pooled-cb de-serialization-fn)]
      (->ConsumerWrapper
        (fn []
          (utils/println-err "Closing pooled consumer.")
          (close consumer))))))

(defn create-producer
  "This is a convenience function for creating a producer for the given broker-url and destination-description.
   
   The created producer implements IFn. Hence, the idiomatic way for using it in Clojure
   is to use the producer as a function to which the data that is to be transmitted is
   passed as single function argument.
  
   By default a single-step producer will be created that immediately sends the passed data.

   Optionally, when passing a pool-size larger than 1, a pooled producer is created.
   A pooled producer does not send the passed data instances individually but groups them in a pool and
   sends the entire pool at once when the pool is filled.
  
   Optionally, a single argument function for customizing the serialization of the pooled-data can be given.
   This defaults to idenitity such that the default serialization of the underlying JMS implementation is used."
  ([broker-url destination-description]
    (create-producer broker-url destination-description 1))
  ([broker-url destination-description pool-size]
    (create-producer broker-url destination-description pool-size identity))
  ([broker-url destination-description pool-size serialization-fn]
    (cond
      (= pool-size 1) (create-single-producer broker-url destination-description serialization-fn)
      (> pool-size 1) (create-pooled-producer broker-url destination-description pool-size serialization-fn)
      :default (utils/println-err "Error: Invalid pool size:" pool-size))))

(defn create-consumer
  "Create a message consumer for receiving data from the specified destination and server/broker.

   The passed callback function (cb) will be called for each message and will receive the data
   from the message as its single argument.
  
   Optionally, when passing a pool-size larger than 1, a pooled consumer is created.
   A pooled consumer receives data in batches and it is the counter part to a pooled producer.

   Optionally, a single argument function for customizing the de-serialization of the transferred data can be given.
   Typically, this should be the inverse operation of the serialization function as used for the producer and defaults to identity."
  ([broker-url destination-description cb]
    (create-consumer broker-url destination-description cb 1))
  ([broker-url destination-description cb pool-size]
    (create-consumer broker-url destination-description cb pool-size identity))
  ([broker-url destination-description cb pool-size de-serialization-fn]
    (cond
      (= pool-size 1) (create-single-consumer broker-url destination-description cb de-serialization-fn)
      (> pool-size 1) (create-pooled-consumer broker-url destination-description cb de-serialization-fn)
      :default (utils/println-err "Error: Invalid pool size:" pool-size))))

(defn create-nippy-producer
  "Create a producer that uses nippy for serialization.

   Optionally, a map of options for customizing the nippy serialization, nippy-opts, can be given.
   This can be used, e.g., for enabling compression or encryption.
   Compression can be enabled with {:compressor taoensso.nippy/lz4-compressor}.
   Possible compressor settings are: taoensso.nippy/lz4-compressor, taoensso.nippy/snappy-compressor, taoensso.nippy/lzma2-compressor.

   For more details about producers please see create-producer."
  ([broker-url destination-description]
    (create-nippy-producer
      broker-url destination-description 1))
  ([broker-url destination-description pool-size]
    (create-nippy-producer
      broker-url destination-description pool-size {:serializable-allowlist *serializable-allowlist*}))
  ([broker-url destination-description pool-size nippy-opts]
    (utils/println-err "Creating nippy producer:" broker-url destination-description pool-size nippy-opts)
    (create-producer
      broker-url
      destination-description
      pool-size
      (fn [data]
        (nippy/freeze data nippy-opts)))))

(defn create-nippy-consumer
  "Create a consumer that uses nippy for de-serialization.

   Optionally, a map of options for customizing the nippy serialization, nippy-opts, can be given.
   See also: create-pooled-nippy-producer.
   For uncompressing compressed data, no options need to be specified as nippy can figure out the employed compression algorithms on its own.

   For more details about consumers and producers please see create-consumer and create-producer."
  ([broker-url destination-description cb]
    (create-nippy-consumer broker-url destination-description cb 1))
  ([broker-url destination-description cb pool-size]
    (create-nippy-consumer broker-url destination-description cb pool-size {:serializable-allowlist *serializable-allowlist*}))
  ([broker-url destination-description cb pool-size nippy-opts]
    (utils/println-err "Creating nippy consumer:" broker-url destination-description pool-size nippy-opts)
    (create-consumer
      broker-url
      destination-description
      cb
      pool-size
      (fn [ba]
        (nippy/thaw ba nippy-opts)))))

(defn create-nippy-lzf-producer
  "Create a producer that uses nippy for serialization and compresses the serialized data with LZF.

   For more details about producers please see create-producer."
  ([broker-url destination-description]
     (create-nippy-lzf-producer broker-url destination-description 1))
  ([broker-url destination-description pool-size]
     (create-nippy-lzf-producer broker-url destination-description pool-size {:serializable-allowlist *serializable-allowlist*}))
  ([broker-url destination-description pool-size nippy-opts]
    (utils/println-err "Creating nippy LZF producer:" broker-url destination-description pool-size nippy-opts)
    (create-producer
      broker-url
      destination-description
      pool-size
      (fn [data]
        (LZFEncoder/encode ^bytes (nippy/freeze data nippy-opts))))))

(defn create-nippy-lzf-consumer
  "Create a consumer that uncompresses the transferred data via LZF and uses nippy for de-serialization.

   For more details about consumers and producers please see create-consumer and create-producer."
  ([broker-url destination-description cb]
    (create-nippy-lzf-consumer broker-url destination-description cb 1))
  ([broker-url destination-description cb pool-size]
    (create-nippy-lzf-consumer broker-url destination-description cb pool-size {:serializable-allowlist *serializable-allowlist*}))
  ([broker-url destination-description cb pool-size nippy-opts]
    (utils/println-err "Creating nippy LZF consumer:" broker-url destination-description pool-size nippy-opts)
    (create-consumer
      broker-url
      destination-description
      cb
      pool-size
      (fn [^bytes ba]
        (nippy/thaw (LZFDecoder/decode ba) nippy-opts)))))

(defn create-carbonite-producer
  "Create a producer that uses carbonite for serialization.

   For more details about producers please see create-producer."
  ([broker-url destination-description]
    (create-carbonite-producer broker-url destination-description 1))
  ([broker-url destination-description pool-size]
    (utils/println-err "Creating carbonite producer:" broker-url destination-description pool-size)
    (let [reg (carb-api/default-registry)]
      (create-producer
        broker-url
        destination-description
        pool-size
        (fn [data]
          (carb-buf/write-bytes reg data))))))

(defn create-carbonite-consumer
  "Create a consumer that uses carbonite for de-serialization.

   For more details about consumers and producers please see create-consumer and create-producer."
  ([broker-url destination-description cb]
    (create-carbonite-consumer broker-url destination-description cb 1))
  ([broker-url destination-description cb pool-size]
    (utils/println-err "Creating carbonite consumer:" broker-url destination-description pool-size)
    (let [reg (carb-api/default-registry)]
      (create-consumer
        broker-url
        destination-description
        cb
        pool-size
        (fn [ba]
          (carb-buf/read-bytes reg ba))))))

(defn create-carbonite-lzf-producer
  "Create a producer that uses carbonite for serialization and compresses the serialized data with LZF.

   For more details about producers please see create-producer."
  ([broker-url destination-description]
    (create-carbonite-lzf-producer broker-url destination-description 1))
  ([broker-url destination-description pool-size]
    (utils/println-err "Creating carbonite LZF producer:" broker-url destination-description pool-size)
    (let [reg (carb-api/default-registry)]
      (create-producer
        broker-url
        destination-description
        pool-size
        (fn [data]
          (LZFEncoder/encode ^bytes (carb-buf/write-bytes reg data)))))))

(defn create-carbonite-lzf-consumer
  "Create a consumer that decompresses the transferred data with LZF and uses carbonite for de-serialization.

   For more details about consumers and producers please see create-consumer and create-producer."
  ([broker-url destination-description cb]
    (create-carbonite-lzf-consumer broker-url destination-description cb 1))
  ([broker-url destination-description cb pool-size]
    (utils/println-err "Creating carbonite LZF consumer:" broker-url destination-description pool-size)
    (let [reg (carb-api/default-registry)]
      (create-consumer
        broker-url
        destination-description
        cb
        pool-size
        (fn [^bytes ba]
          (carb-buf/read-bytes reg (LZFDecoder/decode ^bytes ba)))))))

(defn create-json-producer
  "Create a producer for exchanging data in JSON format.
   
   The passed data will be converted to JSON via Cheshire.
   The JSON string will then be sent as byte array representation.
   The default charset that is used is UTF-8.
   
   One aim of this producer is to enable effortless exchange between various transports, such as, OpenWire, STOMP, WS, or MQTT.

   An optional post-process-fn can be used to further process the byte array data resulting from the serialization.
   One use case for this is, e.g., to compress or encrypt the byte array data.
   Typically, the post-process-fn is expected to accept and return a byte array and defaults to identity.

   For more details about producers please see create-producer."
  ([broker-url destination-description]
    (create-json-producer broker-url destination-description 1))
  ([broker-url destination-description pool-size]
    (create-json-producer broker-url destination-description pool-size identity))
  ([broker-url destination-description pool-size post-process-fn]
    (utils/println-err "Creating JSON producer:" broker-url destination-description pool-size)
    (create-producer
      broker-url
      destination-description
      pool-size
      (fn [data]
        (post-process-fn
          (.getBytes
            ^String (cheshire/generate-string data)
            ^Charset *default-charset*))))))

(defn create-json-consumer
  "Create a consumer for exchanging data in JSON format.
   
   The consumer expects to receive the byte array representation of a string of JSON encoded data.
   The default charset that is used is UTF-8.
   
   One aim of this consumer is to enable effortless exchange between various transports, such as, OpenWire, STOMP, WS, or MQTT.

   An optional pre-process-fn can be used to pre-process the received byte array data before its de-serialization.
   One use case for this is, e.g., to uncompress or decrypt the received byte array data.
   Typically, the pre-process-fn is expected to accept and return a byte array and defaults to identity.
   The pre-process-fn is expected to be the inverse operation of the post-process-fn that was used for the JSON producer (See create-json-producer.).

   For more details about consumers please see create-consumer."
  ([broker-url destination-description cb]
    (create-json-consumer broker-url destination-description cb 1))
  ([broker-url destination-description cb pool-size]
    (create-json-consumer broker-url destination-description cb pool-size identity))
  ([broker-url destination-description cb pool-size pre-process-fn]
    (utils/println-err "Creating JSON consumer:" broker-url destination-description pool-size)
    (create-consumer
      broker-url
      destination-description
      cb
      pool-size
      (fn [^bytes ba]
        (cheshire/parse-string (String. ^bytes (pre-process-fn ba) ^Charset *default-charset*))))))

(defn create-json-lzf-producer
  ([broker-url destination-description]
    (create-json-lzf-producer broker-url destination-description 1))
  ([broker-url destination-description pool-size]
    (create-json-producer
      broker-url
      destination-description
      pool-size
      (fn [^bytes ba]
        (LZFEncoder/encode ba)))))

(defn create-json-lzf-consumer
  ([broker-url destination-description cb]
    (create-json-lzf-consumer broker-url destination-description cb 1))
  ([broker-url destination-description cb pool-size]
    (create-json-consumer
      broker-url
      destination-description
      cb
      pool-size
      (fn [^bytes ba]
        (LZFDecoder/decode ba)))))

(defn create-json-snappy-producer
  ([broker-url destination-description]
    (create-json-snappy-producer broker-url destination-description 1))
  ([broker-url destination-description pool-size]
    (create-json-producer
      broker-url
      destination-description
      pool-size
      (fn [^bytes ba]
        (Snappy/compress ba)))))

(defn create-json-snappy-consumer
  ([broker-url destination-description cb]
    (create-json-snappy-consumer broker-url destination-description cb 1))
  ([broker-url destination-description cb pool-size]
    (create-json-consumer
      broker-url
      destination-description
      cb
      pool-size
      (fn [^bytes ba]
        (Snappy/uncompress ba 0 (alength ba))))))

(defn create-failsafe-json-consumer
  ([broker-url destination-description cb]
    (create-failsafe-json-consumer broker-url destination-description cb 1))
  ([broker-url destination-description cb pool-size]
    (create-failsafe-json-consumer broker-url destination-description cb pool-size identity))
  ([broker-url destination-description cb pool-size pre-process-fn]
    (utils/println-err "Creating failsafe JSON consumer:" broker-url destination-description pool-size)
    (create-consumer
      broker-url
      destination-description
      cb
      pool-size
      (fn [msg-payload]
        (condp instance? msg-payload
          utils/byte-array-type (try
                                  (cheshire/parse-string
                                    (String.
                                      ^bytes (pre-process-fn ^bytes msg-payload)
                                      ^Charset *default-charset*))
                                  (catch Exception e1
                                    (try
                                      (String.
                                        ^bytes (pre-process-fn ^bytes msg-payload)
                                        ^Charset *default-charset*)
                                      (catch Exception e2
                                        (str (vec msg-payload))))))
          String (try
                   (cheshire/parse-string msg-payload)
                   (catch Exception e
                     msg-payload))
          (str msg-payload))))))

