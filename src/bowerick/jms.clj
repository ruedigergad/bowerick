;;;
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
    [clojure.java.io :refer :all]
    [clj-assorted-utils.util :refer :all])
  (:import
    (bowerick JmsProducer PooledBytesMessageProducer)
    (clojure.lang IFn)
    (com.esotericsoftware.kryo Kryo)
    (com.esotericsoftware.kryo.io Input Output)
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
    (org.fusesource.stomp.jms StompJmsConnectionFactory)))

(def ^:dynamic ^Long *kryo-output-size* 2048000)

(def ^:dynamic *user-name* nil)
(def ^:dynamic *user-password* nil)

(def ^:dynamic *trust-store-file* "client.ts")
(def ^:dynamic *trust-store-password* "password")
(def ^:dynamic *key-store-file* "client.ks")
(def ^:dynamic *key-store-password* "password")

; See also: http://activemq.apache.org/objectmessage.html
(def ^:dynamic *serializable-packages*
  '("clojure.lang"
    "java.lang"
    "java.util"
    "org.apache.activemq"
    "org.fusesource.hawtbuf"
    "com.thoughtworks.xstream.mapper"))

(defn start-broker
  ([address]
   (doto (BrokerService.)
     (.addConnector address)
     (.setPersistent false)
     (.setUseJmx false)
     (.start)))
  ([address allow-anon users permissions]
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
         authorization-plugin (AuthorizationPlugin. authorization-map)]
     (doto (BrokerService.)
       (.addConnector address)
       (.setPersistent false)
       (.setUseJmx false)
       (.setPlugins (into-array org.apache.activemq.broker.BrokerPlugin [authentication-plugin authorization-plugin]))
       (.start)))))

(defn get-destinations
  ([broker-service]
    (get-destinations broker-service true))
  ([^BrokerService broker-service include-destinations-without-producers]
    (let [dst-vector (ref [])]
      (doseq [^Destination dst (vec (-> (.getBroker broker-service) (.getDestinationMap) (.values)))]
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
      @dst-vector)))

(defn send-error-msg [producer msg]
  (println msg)
  (producer (str "reply error " msg)))

(defn get-adjusted-ssl-context
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

(defmacro with-endpoint
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

(defn init-endpoint
  [^String server-url ^String endpoint-description]
  (with-endpoint
    server-url
    endpoint-description
    (.close connection)
    endpoint))

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

(defn create-producer
  [^String server-url ^String endpoint-description]
  (println "Creating producer for endpoint description:" endpoint-description)
  (with-endpoint server-url endpoint-description
    (let [producer (doto
                     (.createProducer session endpoint)
                     (.setDeliveryMode DeliveryMode/NON_PERSISTENT))]
      (->ProducerWrapper
        (fn [data]
          (condp instance? data
            byte-array-type (.send
                              producer
                              (doto
                                (.createBytesMessage session)
                                (.writeBytes ^bytes data)))
            java.lang.String (.send
                               producer
                               (.createTextMessage session ^String data))
            (.send
              producer
              (.createObjectMessage session data))))
        (fn []
          (println "Closing producer for endpoint description:" endpoint-description)
          (.close connection))))))

(defrecord ConsumerWrapper [close-fn]
  AutoCloseable
    (close [this]
      (close-fn)))

(defn create-consumer [^String server-url ^String endpoint-description cb]
  (println "Creating consumer for endpoint description:" endpoint-description)
  (with-endpoint server-url endpoint-description
    (let [listener (proxy [MessageListener] []
                     (onMessage [^Message m]
                       (condp instance? m
                         BytesMessage (let [data (byte-array (.getBodyLength ^BytesMessage m))]
                                        (.readBytes ^BytesMessage m data)
                                        (cb data))
                         ObjectMessage  (try
                                          (cb (.getObject ^ObjectMessage m))
                                          (catch javax.jms.JMSException e
                                            (println e)))
                         TextMessage (cb (.getText ^TextMessage m))
                         (println "Unknown message type:" (type m)))))
          consumer (doto
                     (.createConsumer session endpoint)
                     (.setMessageListener listener))]
      (->ConsumerWrapper
        (fn []
          (println "Closing consumer for endpoint description:" endpoint-description)
          (.close connection))))))

(defn close [s]
  (.close s))

(defn create-pooled-producer [server-url endpoint-description ^long pool-size]
  (println "Creating pooled producer for endpoint description:" endpoint-description)
  (let [producer (create-producer server-url endpoint-description)
        pool (ArrayList. pool-size)]
    (->ProducerWrapper
      (fn [o]
        (do
          (.add pool o)
          (when (>= (.size pool) pool-size)
            (producer pool)
            (.clear pool))))
      (fn []
        (println "Closing pooled producer for endpoint description:" endpoint-description)
        (.close producer)))))

(defn create-pooled-consumer [^String server-url ^String endpoint-description cb]
  (println "Creating pooled consumer for endpoint description:" endpoint-description)
  (with-endpoint server-url endpoint-description
    (let [listener (proxy [MessageListener] []
                     (onMessage [^Message m]
                       (doseq [o ^ArrayList (.getObject ^ObjectMessage m)]
                         (cb o))))
          consumer (doto
                     (.createConsumer session endpoint)
                     (.setMessageListener listener))]
      (->ConsumerWrapper
        (fn []
          (println "Closing pooled consumer for endpoint description:" endpoint-description)
          (.close connection))))))

(defn create-pooled-kryo-producer
  ([server-url endpoint-description pool-size]
    (create-pooled-kryo-producer
      server-url endpoint-description pool-size (fn [^bytes ba] ba)))
  ([server-url endpoint-description ^long pool-size ba-out-fn]
    (println "Creating pooled kryo producer for endpoint description:" endpoint-description)
    (let [producer (create-producer server-url endpoint-description)
          pool (ArrayList. pool-size)
          out (Output. *kryo-output-size*)
          kryo (Kryo.)]
      (->ProducerWrapper
        (fn [o]
          (do
            (.add pool o)
            (when (>= (.size pool) pool-size)
              (let [obj (.writeObject kryo out pool)
                    ^bytes b-array (ba-out-fn (.toBytes out))]
                (producer b-array)
                (.clear out)
                (.clear pool)))))
        (fn []
          (println "Closing pooled kryo producer for endpoint description:" endpoint-description)
          (.close producer))))))

(defn create-pooled-kryo-consumer
  ([server-url endpoint-description cb]
    (create-pooled-kryo-consumer
      server-url endpoint-description cb (fn [^bytes ba] ba)))
  ([^String server-url ^String endpoint-description cb ba-in-fn]
    (println "Creating pooled kryo consumer for endpoint description:" endpoint-description)
    (with-endpoint server-url endpoint-description
      (let [kryo (Kryo.)
            in (Input.)
            listener (proxy [MessageListener] []
                       (onMessage [^Message m]
                         (let [data (byte-array (.getBodyLength ^BytesMessage m))]
                           (.readBytes ^BytesMessage m data)
                           (.setBuffer in (ba-in-fn data))
                           (doseq [o ^ArrayList (.readObject kryo in ArrayList)]
                             (cb o)))))
            consumer (doto
                       (.createConsumer session endpoint)
                       (.setMessageListener listener))]      
        (->ConsumerWrapper
          (fn []
            (println "Closing pooled kryo consumer for endpoint description:" endpoint-description)
            (.close connection)))))))

(defn create-pooled-lzf-producer
  [server-url endpoint-description pool-size]
  (println "Creating pooled-lzf-producer for endpoint description:" endpoint-description)
  (create-pooled-kryo-producer
    server-url
    endpoint-description
    pool-size
    (fn [^bytes ba]
      (LZFEncoder/encode ba))))

(defn create-pooled-lzf-consumer [server-url endpoint-description cb]
  (println "Creating pooled-lzf-consumer for endpoint description:" endpoint-description)
  (create-pooled-kryo-consumer
    server-url
    endpoint-description
    cb
    (fn [^bytes ba] (LZFDecoder/decode ba))))

;(defn create-pooled-bytes-message-producer [^String server-url ^String endpoint-description pool-size]
;  (println "Creating pooled-bytes-message-producer for endpoint description:" endpoint-description)
;  (with-endpoint server-url endpoint-description
;    (let [producer (doto
;                     (.createProducer session endpoint)
;                     (.setDeliveryMode DeliveryMode/NON_PERSISTENT))]
;      (PooledBytesMessageProducer. producer session connection pool-size))))

