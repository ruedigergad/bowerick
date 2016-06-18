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
  (:use
    [clojure.string :only (join split)])
  (:require
    [carbonite.api :as carb-api]
    [carbonite.buffer :as carb-buf]
    [cheshire.core :refer :all]
    [clojure.java.io :refer :all]
    [clj-assorted-utils.util :refer :all]
    [taoensso.nippy :as nippy])
  (:import
    (bowerick JmsProducer PooledBytesMessageProducer)
    (clojure.lang IFn)
    (com.ning.compress.lzf LZFDecoder LZFEncoder)
    (java.lang AutoCloseable)
    (java.security KeyStore)
    (java.util ArrayList)
    (java.util.concurrent ArrayBlockingQueue)
    (javax.jms BytesMessage Connection DeliveryMode Message MessageProducer MessageListener ObjectMessage Session TextMessage Topic)
    (javax.net.ssl KeyManagerFactory SSLContext TrustManagerFactory)
    (org.apache.activemq ActiveMQConnectionFactory ActiveMQSslConnectionFactory)
    (org.apache.activemq.broker BrokerService)
    (org.apache.activemq.broker.region Destination)
    (org.apache.activemq.security AuthenticationUser AuthorizationEntry AuthorizationMap AuthorizationPlugin DefaultAuthorizationMap SimpleAuthenticationPlugin)
    (org.fusesource.stomp.jms StompJmsConnectionFactory)
    (org.springframework.messaging.converter ByteArrayMessageConverter SmartMessageConverter StringMessageConverter)
    (org.springframework.messaging.simp.stomp StompFrameHandler StompHeaders StompSession StompSessionHandler StompSessionHandlerAdapter)
    (org.springframework.web.socket.client WebSocketClient)
    (org.springframework.web.socket.messaging WebSocketStompClient)
    (org.springframework.web.socket.client.standard StandardWebSocketClient)))

(def ^:dynamic *user-name* nil)
(def ^:dynamic *user-password* nil)

(def ^:dynamic *trust-store-file* "client.ts")
(def ^:dynamic *trust-store-password* "password")
(def ^:dynamic *key-store-file* "client.ks")
(def ^:dynamic *key-store-password* "password")

(def ^:dynamic *broker-management-command-topic* "/topic/bowerick.broker.management.command")
(def ^:dynamic *broker-management-reply-topic* "/topic/bowerick.broker.management.reply")

; See also: http://activemq.apache.org/objectmessage.html
(def ^:dynamic *serializable-packages*
  '("clojure.lang"
    "java.lang"
    "java.math"
    "java.util"
    "org.apache.activemq"
    "org.fusesource.hawtbuf"
    "com.thoughtworks.xstream.mapper"))

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
                                  (input-stream *key-store-file*)
                                  (char-array *key-store-password*)))
                              (char-array *key-store-password*)))
        trustManagerFactory (doto
                              (TrustManagerFactory/getInstance "SunX509")
                              (.init
                                (doto (KeyStore/getInstance "JKS")
                                  (.load
                                    (input-stream *trust-store-file*)
                                    (char-array *trust-store-password*)))))]
    (doto
      (SSLContext/getInstance "TLS")
      (.init
        (.getKeyManagers keyManagerFactory)
        (.getTrustManagers trustManagerFactory)
        nil))))

(defn adjust-default-ssl-context
  "Set the default SSLContext to a context that is initialized with key and
   trust stores based on the settings defined in the global dynamic vars:
   *key-store-file* *key-store-password*
   *trust-store-file* *trust-store-password*"
  []
  (when
    (and
      (file-exists? *key-store-file*)
      (file-exists? *trust-store-file*))
    (println
      "Setting default SSLContext to use key store file:"
      *key-store-file*
      "and trust store file:"
      *trust-store-file*)
    (SSLContext/setDefault (get-adjusted-ssl-context))))

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
  (println msg)
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
                                  (println "Setting permission:" perm)
                                  (let [trgt (perm "target")
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
                                    (condp = (perm "type")
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
                 (.setPersistent false)
                 (.setUseJmx false)
                 (.setPlugins
                   (into-array
                     org.apache.activemq.broker.BrokerPlugin
                     [authentication-plugin authorization-plugin])))]
    broker))

(declare create-single-producer)
(declare create-single-consumer)
(declare close)

(defn start-broker
  "Start an embedded ActiveMQ broker.
   Examples for valid addresses are: 
  
   tcp://127.0.0.1:42424 udp://127.0.0.1:42426
   ssl://127.0.0.1:42425 ssl://127.0.0.1:42425?needClientAuth=true
   stomp+ssl://127.0.0.1:42423 stomp+ssl://127.0.0.1:42423?needClientAuth=true
   
   In addition to the address, it is possible to configure the access control:
   When allow-anon is true, anonymous access is allowed.
   The list of users is defined as a vector of maps, e.g.:

   [{\"name\" \"test-user\", \"password\" \"secret\", \"groups\" \"test-group,admins,publishers,consumers\"}]
   
   Permissions are also defined as a vector of maps, e.g.:
   
   [{\"target\" \"test.topic.a\", \"type\" \"topic\", \"write\" \"anonymous\"}"
  ([address]
    (start-broker address nil nil nil))
  ([address allow-anon users permissions]
    (adjust-default-ssl-context)
    (let [broker (if
                   (and allow-anon users permissions)
                   (setup-broker-with-auth allow-anon users permissions)
                   (doto
                     (BrokerService.)
                     (.setPersistent false)
                     (.setUseJmx false)))
          _ (if (sequential? address)
              (doseq [addr address]
                (.addConnector broker addr))
              (.addConnector broker address))
          _ (.start broker)
          producer (try
                     (println "Info: Enabling management producer at:" *broker-management-reply-topic*)
                     (binding [*trust-store-file* *key-store-file*
                               *trust-store-password* *key-store-password*
                               *key-store-file* *trust-store-file*
                               *key-store-password* *trust-store-password*]
                       (create-single-producer
                         address
                         *broker-management-reply-topic*
                         generate-string))
                     (catch Exception e
                       (println "Warning: Could not create management producer for:" *broker-management-reply-topic*)))
          consumer (try
                     (println "Info: Enabling management consumer at:" *broker-management-command-topic*)
                     (binding [*trust-store-file* *key-store-file*
                               *trust-store-password* *key-store-password*
                               *key-store-file* *trust-store-file*
                               *key-store-password* *trust-store-password*]
                       (create-single-consumer
                         address
                         *broker-management-command-topic*
                         (fn [cmd]
                           (condp = cmd
                             "get-destinations" (producer (get-destinations broker false))
                             "get-all-destinations" (producer (get-destinations broker true))
                             (send-error-msg producer (str "Unknown command: " cmd))))
                         parse-string))
                     (catch Exception e
                       (println "Warning: Could not create management consumer for:" *broker-management-command-topic*)))]
      (.waitUntilStarted broker)
      {:broker broker
       :stop (fn []
               (if consumer
                 (close consumer))
               (if producer
                 (close producer))
               (.stop broker)
               (.waitUntilStopped broker))})))

(defn stop
  [brkr]
  ((:stop brkr)))

(defmacro with-endpoint
  "Execute body in a context for which connection, session, and endpoint are
   made available based on the provided server-url and endpoint-description.
   
   The server-url is the address of the broker to which a connection shall
   be establishd. Examples for valid address schemes are given in the doc
   string of start-broker.
  
   Endpoint descriptions have the form \"/<type>/<name>\" for which \"<type>\"
   is currently either \"topic\" or \"queue\" and the \"<name>\" is the unique
   name of the endpoint."
  [server-url endpoint-description & body]
  `(let [factory# (cond
                    (or (.startsWith ~server-url "ssl:")
                        (.startsWith ~server-url "tls:"))
                      (doto
                        (ActiveMQSslConnectionFactory.
                          (if
                            (.contains ~server-url "?")
                            (.substring ~server-url 0 (.indexOf ~server-url "?"))
                            ~server-url))
                        (.setTrustStore *trust-store-file*) (.setTrustStorePassword *trust-store-password*)
                        (.setKeyStore *key-store-file*) (.setKeyStorePassword *key-store-password*)
                        (.setTrustedPackages *serializable-packages*))
                    (.startsWith ~server-url "stomp:")
                      (doto
                        (StompJmsConnectionFactory.)
                        (.setBrokerURI (.replaceFirst ~server-url "stomp" "tcp")))
                    (.startsWith ~server-url "stomp+ssl:")
                      (doto
                        (StompJmsConnectionFactory.)
                        (.setSslContext (get-adjusted-ssl-context))
                        (.setBrokerURI (.replaceFirst ~server-url "stomp\\+ssl" "ssl")))
                    :default (doto
                               (ActiveMQConnectionFactory. ~server-url)
                               (.setTrustedPackages *serializable-packages*)))
         ~'connection (doto
                        (if (and (not (nil? *user-name*)) (not (nil? *user-password*)))
                          (do
                            (println "Creating connection for user:" *user-name*)
                            (.createConnection factory# *user-name* *user-password*))
                          (do
                            (println "Creating connection.")
                            (.createConnection factory#)))
                        (.start))
         ~'session ~(with-meta
                      `(.createSession ~'connection false Session/AUTO_ACKNOWLEDGE)
                      {:tag 'javax.jms.Session})
         split-endpoint# (filter #(not= % "") (split ~endpoint-description #"/"))
         endpoint-type# (first split-endpoint#)
         endpoint-name# (join "/" (rest split-endpoint#))
         _# (println "Creating endpoint. Type:" endpoint-type# "Name:" endpoint-name#)
         ~'endpoint (condp = endpoint-type#
                      "topic" (.createTopic ~'session endpoint-name#)
                      "queue" (.createQueue ~'session endpoint-name#)
                      (println "Could not create endpoint. Type:" endpoint-type# "Name:" endpoint-name#))]
     ~@body))

(defrecord ProducerWrapper [send-fn close-fn]
  AutoCloseable
    (close [this]
      (close-fn))
  JmsProducer
    (sendData [this data]
      (send-fn data))
  IFn
    (invoke [this data]
      (send-fn data)))

(defn create-single-producer
  "Create a message producer for sending data to the specified endpoint and server/broker.

   The created producer implements IFn. Hence, the idiomatic way for using it in Clojure
   is to use the producer as a function to which the data that is to be transmitted is
   passed as single function argument.
  
   For each invocation, the passed data will be transmitted in a separate message.
   
   Optionally, a single argument function for customizing the serialization of the data can be given.
   This defaults to idenitity such that the default serialization of the underlying JMS implementation is used.
  
   This function is not intended to be used directly.
   It is recommended to use create-producer or the various create-XXXXXX-producer derivatives that employ
   customized serialization mechanisms instead."
  ([server-url endpoint-description]
    (create-single-producer server-url endpoint-description identity))
  ([^String server-url ^String endpoint-description serialization-fn]
    (println "Creating producer for endpoint description:" endpoint-description)
    (cond
      (.startsWith server-url "ws") (let [ws-client (StandardWebSocketClient.)
                                          ws-stomp-client (WebSocketStompClient. ws-client)
                                          session (atom nil)
                                          flag (prepare-flag)]
                                      (.setMessageConverter ws-stomp-client (StringMessageConverter.))
                                      (.connect
                                        ws-stomp-client
                                        server-url
                                        (proxy [StompSessionHandlerAdapter] []
                                          (afterConnected [^StompSession new-session ^StompHeaders stomp-headers]
                                            (reset! session new-session)
                                            (set-flag flag)))
                                        (await-flag flag)
                                        (->ProducerWrapper
                                          (fn [data]
                                            (.send ^StompSession @session) ^String endpoint-description)
                                          (fn []
                                            (println "Closing producer for endpoint description:" endpoint-description)
                                            (println "FIXME: Properly close websocket connection.")))))
      :default (with-endpoint server-url endpoint-description
                 (let [producer (doto
                                  (.createProducer session endpoint)
                                  (.setDeliveryMode DeliveryMode/NON_PERSISTENT))]
                   (->ProducerWrapper
                     (fn [data]
                       (let [serialized-data (serialization-fn data)]
                         (condp instance? serialized-data
                           byte-array-type (.send
                                             producer
                                             (doto
                                               (.createBytesMessage session)
                                               (.writeBytes ^bytes serialized-data)))
                           java.lang.String (.send
                                              producer
                                              (doto
                                                (.createTextMessage session ^String serialized-data)
                                                (.setStringProperty "transformation" "TEXT")))
                           (.send
                             producer
                             (.createObjectMessage session serialized-data)))))
                     (fn []
                       (println "Closing producer for endpoint description:" endpoint-description)
                       (.close connection))))))))

(defrecord ConsumerWrapper [close-fn]
  AutoCloseable
    (close [this]
      (close-fn)))

(defn create-single-consumer
  "Create a message consumer for receiving data from the specified endpoint and server/broker.

   The passed callback function (cb) will be called for each message and will receive the data
   from the message as its single argument.
  
   Optionally, a single argument function for customizing the de-serialization of the transferred data can be given.
   Typically, this should be the inverse operation of the serialization function as used for the producer and defaults to identity.
  
   This function is not intended to be used directly.
   It is recommended to use create-consumer or the various create-XXXXXX-consumer derivatives that employ
   customized serialization mechanisms instead."
  ([server-url endpoint-description cb]
    (create-single-consumer server-url endpoint-description cb identity))
  ([^String server-url ^String endpoint-description cb de-serialization-fn]
    (println "Creating consumer for endpoint description:" endpoint-description)
    (with-endpoint server-url endpoint-description
      (let [listener (proxy [MessageListener] []
                       (onMessage [^Message m]
                         (condp instance? m
                           BytesMessage (let [data (byte-array (.getBodyLength ^BytesMessage m))]
                                          (.readBytes ^BytesMessage m data)
                                          (cb (de-serialization-fn data)))
                           ObjectMessage  (try
                                            (cb (de-serialization-fn (.getObject ^ObjectMessage m)))
                                            (catch javax.jms.JMSException e
                                              (println e)))
                           TextMessage (cb (de-serialization-fn (.getText ^TextMessage m)))
                           (println "Unknown message type:" (type m)))))
            consumer (doto
                       (.createConsumer session endpoint)
                       (.setMessageListener listener))]
        (->ConsumerWrapper
          (fn []
            (println "Closing consumer for endpoint description:" endpoint-description)
            (.close connection)))))))

(defn close
  "Close a producer or consumer."
  [o]
  (.close o))

(defn create-pooled-producer
  "Create a pooled producer with the given pool-size for the given server-url and endpoint-description.
  
   A pooled producer does not send the passed data instances individually but groups them in a pool and
   sends the entire pool at once when the pool is filled.
  
   Optionally, a single argument function for customizing the serialization of the pooled-data can be given.
   This defaults to idenitity such that the default serialization of the underlying JMS implementation is used.
  
   This function is not intended to be used directly.
   It is recommended to use create-producer or the various create-XXXXXX-producer derivatives that employ
   customized serialization mechanisms instead."
  ([server-url endpoint-description pool-size]
    (create-pooled-producer server-url endpoint-description pool-size identity))
  ([server-url endpoint-description ^long pool-size serialization-fn]
    (println "Creating pooled producer for endpoint description:" endpoint-description "; Pool size:" pool-size)
    (let [producer (create-single-producer server-url endpoint-description serialization-fn)
          pool (ArrayList. pool-size)]
      (->ProducerWrapper
        (fn [o]
          (.add pool o)
          (when (>= (.size pool) pool-size)
            (producer pool)
            (.clear pool)))
        (fn []
          (println "Closing pooled producer for endpoint description:" endpoint-description)
          (.close producer))))))

(defn create-pooled-consumer
  "Create a consumer for receiving pooled data.
   
   The calback function, cb, will be called for each data instance in the pool individually.
  
   Optionally, a single argument function for customizing the de-serialization of the transferred data can be given.
   Typically, this should be the inverse operation of the serialization function as used for the pooled producer and defaults to identity.
  
   This function is not intended to be used directly.
   It is recommended to use create-consumer or the various create-XXXXXX-consumer derivatives that employ
   customized serialization mechanisms instead."
  ([server-url endpoint-description cb]
    (create-pooled-consumer server-url endpoint-description cb identity))
  ([server-url endpoint-description cb de-serialization-fn]
    (println "Creating pooled consumer for endpoint description:" endpoint-description)
    (let [pooled-cb (fn [^ArrayList lst]
                      (doseq [o lst]
                        (cb o)))
          consumer (create-single-consumer server-url endpoint-description pooled-cb de-serialization-fn)]
      (->ConsumerWrapper
        (fn []
          (println "Closing pooled consumer for endpoint description:" endpoint-description)
          (close consumer))))))

(defn create-producer
  "This is a convenience function for creating a producer for the given server-url and endpoint-description.
   
   The created producer implements IFn. Hence, the idiomatic way for using it in Clojure
   is to use the producer as a function to which the data that is to be transmitted is
   passed as single function argument.
  
   By default a single-step producer will be created that immediately sends the passed data.

   Optionally, when passing a pool-size larger than 1, a pooled producer is created.
   A pooled producer does not send the passed data instances individually but groups them in a pool and
   sends the entire pool at once when the pool is filled.
  
   Optionally, a single argument function for customizing the serialization of the pooled-data can be given.
   This defaults to idenitity such that the default serialization of the underlying JMS implementation is used."
  ([server-url endpoint-description]
    (create-producer server-url endpoint-description 1))
  ([server-url endpoint-description pool-size]
    (create-producer server-url endpoint-description pool-size identity))
  ([server-url endpoint-description pool-size serialization-fn]
    (cond
      (= pool-size 1) (create-single-producer server-url endpoint-description serialization-fn)
      (> pool-size 1) (create-pooled-producer server-url endpoint-description pool-size serialization-fn)
      :default (println "Error: Invalid pool size:" pool-size))))

(defn create-consumer
  "Create a message consumer for receiving data from the specified endpoint and server/broker.

   The passed callback function (cb) will be called for each message and will receive the data
   from the message as its single argument.
  
   Optionally, when passing a pool-size larger than 1, a pooled consumer is created.
   A pooled consumer receives data in batches and it is the counter part to a pooled producer.

   Optionally, a single argument function for customizing the de-serialization of the transferred data can be given.
   Typically, this should be the inverse operation of the serialization function as used for the producer and defaults to identity."
  ([server-url endpoint-description cb]
    (create-consumer server-url endpoint-description cb 1))
  ([server-url endpoint-description cb pool-size]
    (create-consumer server-url endpoint-description cb pool-size identity))
  ([server-url endpoint-description cb pool-size de-serialization-fn]
    (cond
      (= pool-size 1) (create-single-consumer server-url endpoint-description cb de-serialization-fn)
      (> pool-size 1) (create-pooled-consumer server-url endpoint-description cb de-serialization-fn)
      :default (println "Error: Invalid pool size:" pool-size))))

(defn create-nippy-producer
  "Create a producer that uses nippy for serialization.

   Optionally, a map of options for customizing the nippy serialization, nippy-opts, can be given.
   This can be used, e.g., for enabling compression or encryption.
   Compression can be enabled with {:compressor taoensso.nippy/lz4-compressor}.
   Possible compressor settings are: taoensso.nippy/lz4-compressor, taoensso.nippy/snappy-compressor, taoensso.nippy/lzma2-compressor.

   For more details about producers please see create-producer."
  ([server-url endpoint-description]
    (create-nippy-producer
      server-url endpoint-description 1))
  ([server-url endpoint-description pool-size]
    (create-nippy-producer
      server-url endpoint-description pool-size {}))
  ([server-url endpoint-description pool-size nippy-opts]
    (println "Creating nippy producer for endpoint description:" endpoint-description
             "with options:" nippy-opts)
    (create-producer
      server-url
      endpoint-description
      pool-size
      (fn [data]
        (nippy/freeze data nippy-opts)))))

(defn create-nippy-consumer
  "Create a consumer that uses nippy for de-serialization.

   Optionally, a map of options for customizing the nippy serialization, nippy-opts, can be given.
   See also: create-pooled-nippy-producer.
   For uncompressing compressed data, no options need to be specified as nippy can figure out the employed compression algorithms on its own.

   For more details about consumers and producers please see create-consumer and create-producer."
  ([server-url endpoint-description cb]
    (create-nippy-consumer server-url endpoint-description cb 1))
  ([server-url endpoint-description cb pool-size]
    (create-nippy-consumer server-url endpoint-description cb pool-size {}))
  ([server-url endpoint-description cb pool-size nippy-opts]
    (create-consumer
      server-url
      endpoint-description
      cb
      pool-size
      (fn [ba]
        (nippy/thaw ba nippy-opts)))))

(defn create-nippy-lzf-producer
  "Create a producer that uses nippy for serialization and compresses the serialized data with LZF.

   For more details about producers please see create-producer."
  ([server-url endpoint-description]
     (create-nippy-lzf-producer server-url endpoint-description 1))
  ([server-url endpoint-description pool-size]
     (create-nippy-lzf-producer server-url endpoint-description pool-size {}))
  ([server-url endpoint-description pool-size nippy-opts]
    (println "Creating nippy lzf producer for endpoint description:" endpoint-description)
    (create-producer
      server-url
      endpoint-description
      pool-size
      (fn [data]
        (LZFEncoder/encode ^bytes (nippy/freeze data nippy-opts))))))

(defn create-nippy-lzf-consumer
  "Create a consumer that uncompresses the transferred data via LZF and uses nippy for de-serialization.

   For more details about consumers and producers please see create-consumer and create-producer."
  ([server-url endpoint-description cb]
    (create-nippy-lzf-consumer server-url endpoint-description cb 1))
  ([server-url endpoint-description cb pool-size]
    (create-nippy-lzf-consumer server-url endpoint-description cb pool-size {}))
  ([server-url endpoint-description cb pool-size nippy-opts]
    (println "Creating nippy lzf consumer for endpoint description:" endpoint-description)
    (create-consumer
      server-url
      endpoint-description
      cb
      pool-size
      (fn [^bytes ba]
        (nippy/thaw (LZFDecoder/decode ba) nippy-opts)))))

(defn create-carbonite-producer
  "Create a producer that uses carbonite for serialization.

   For more details about producers please see create-producer."
  ([server-url endpoint-description]
    (create-carbonite-producer server-url endpoint-description 1))
  ([server-url endpoint-description pool-size]
    (println "Creating carbonite producer for endpoint description:" endpoint-description)
    (let [reg (carb-api/default-registry)]
      (create-producer
        server-url
        endpoint-description
        pool-size
        (fn [data]
          (carb-buf/write-bytes reg data))))))

(defn create-carbonite-consumer
  "Create a consumer that uses carbonite for de-serialization.

   For more details about consumers and producers please see create-consumer and create-producer."
  ([server-url endpoint-description cb]
    (create-carbonite-consumer server-url endpoint-description cb 1))
  ([server-url endpoint-description cb pool-size]
    (println "Creating carbonite consumer for endpoint description:" endpoint-description)
    (let [reg (carb-api/default-registry)]
      (create-consumer
        server-url
        endpoint-description
        cb
        pool-size
        (fn [ba]
          (carb-buf/read-bytes reg ba))))))

(defn create-carbonite-lzf-producer
  "Create a producer that uses carbonite for serialization and compresses the serialized data with LZF.

   For more details about producers please see create-producer."
  ([server-url endpoint-description]
    (create-carbonite-lzf-producer server-url endpoint-description 1))
  ([server-url endpoint-description pool-size]
    (println "Creating carbonite lzf producer for endpoint description:" endpoint-description)
    (let [reg (carb-api/default-registry)]
      (create-producer
        server-url
        endpoint-description
        pool-size
        (fn [data]
          (LZFEncoder/encode ^bytes (carb-buf/write-bytes reg data)))))))

(defn create-carbonite-lzf-consumer
  "Create a consumer that decompresses the transferred data with LZF and uses carbonite for de-serialization.

   For more details about consumers and producers please see create-consumer and create-producer."
  ([server-url endpoint-description cb]
    (create-carbonite-lzf-consumer server-url endpoint-description cb 1))
  ([server-url endpoint-description cb pool-size]
    (println "Creating carbonite lzf consumer for endpoint description:" endpoint-description)
    (let [reg (carb-api/default-registry)]
      (create-consumer
        server-url
        endpoint-description
        cb
        pool-size
        (fn [^bytes ba]
          (carb-buf/read-bytes reg (LZFDecoder/decode ^bytes ba)))))))

;(defn create-pooled-bytes-message-producer [^String server-url ^String endpoint-description pool-size]
;  (println "Creating pooled-bytes-message-producer for endpoint description:" endpoint-description)
;  (with-endpoint server-url endpoint-description
;    (let [producer (doto
;                     (.createProducer session endpoint)
;                     (.setDeliveryMode DeliveryMode/NON_PERSISTENT))]
;      (PooledBytesMessageProducer. producer session connection pool-size))))

