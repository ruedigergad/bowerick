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
    (bowerick PooledBytesMessageProducer)
    (com.esotericsoftware.kryo Kryo)
    (com.esotericsoftware.kryo.io Input Output)
    (com.ning.compress.lzf LZFDecoder LZFEncoder)
    (java.security KeyStore)
    (java.util ArrayList)
    (java.util.concurrent ArrayBlockingQueue)
    (javax.jms BytesMessage Connection DeliveryMode Message MessageProducer MessageListener ObjectMessage Session TextMessage Topic)
    (javax.net.ssl KeyManagerFactory SSLContext TrustManagerFactory)
    (org.apache.activemq ActiveMQConnectionFactory ActiveMQSslConnectionFactory)
    (org.apache.activemq.broker BrokerService)
    (org.apache.activemq.security AuthenticationUser AuthorizationEntry AuthorizationMap AuthorizationPlugin DefaultAuthorizationMap SimpleAuthenticationPlugin)
    (org.fusesource.stomp.jms StompJmsConnectionFactory)))

(def ^:dynamic *kryo-output-size* 2048000)

(def ^:dynamic *user-name* nil)
(def ^:dynamic *user-password* nil)

(def ^:dynamic *trust-store-file* "client.ts")
(def ^:dynamic *trust-store-password* "password")
(def ^:dynamic *key-store-file* "client.ks")
(def ^:dynamic *key-store-password* "password")

(defn start-broker
  ([address]
   (doto (BrokerService.)
     (.addConnector address)
     (.setPersistent false)
     (.setUseJmx false)
     (.start)))
  ([address allow-anon users permissions]
   (let [user-list (map (fn [u] (AuthenticationUser. (u "name") (u "password") (u "groups"))) users)
         authentication-plugin (doto (SimpleAuthenticationPlugin. user-list)
                                (.setAnonymousAccessAllowed allow-anon)
                                (.setAnonymousUser "anonymous")
                                (.setAnonymousGroup "anonymous"))
         authorization-entries (map (fn [perm]
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
         authorization-map (doto (DefaultAuthorizationMap.)
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
  ([broker-service include-destinations-without-producers]
    (let [dst-vector (ref [])]
      (doseq [dst (vec (-> (.getBroker broker-service) (.getDestinationMap) (.values)))]
        (if (or include-destinations-without-producers (> (-> (.getDestinationStatistics dst) (.getProducers) (.getCount)) 0))
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

(defn get-adjusted-ssl-context []
  (let [keyManagerFactory (doto (KeyManagerFactory/getInstance "SunX509")
                            (.init (doto (KeyStore/getInstance "JKS")
                                     (.load (input-stream *key-store-file*) (char-array *key-store-password*)))
                                   (char-array *key-store-password*)))
        trustManagerFactory (doto (TrustManagerFactory/getInstance "SunX509")
                              (.init (doto (KeyStore/getInstance "JKS")
                                       (.load (input-stream *trust-store-file*) (char-array *trust-store-password*)))))]
    (doto (SSLContext/getInstance "TLS")
      (.init (.getKeyManagers keyManagerFactory) (.getTrustManagers trustManagerFactory) nil))))

(defmacro with-endpoint [server endpoint-description & body]
  `(let [factory# (cond
                    (or (.startsWith ~server "ssl:")
                        (.startsWith ~server "tls:"))
                      (doto (ActiveMQSslConnectionFactory. (if (.contains ~server "?")
                                                             (.substring ~server 0 (.indexOf ~server "?"))
                                                             ~server))
                        (.setTrustStore *trust-store-file*) (.setTrustStorePassword *trust-store-password*)
                        (.setKeyStore *key-store-file*) (.setKeyStorePassword *key-store-password*))
                    (.startsWith ~server "stomp:")
                      (doto (StompJmsConnectionFactory.) (.setBrokerURI (.replaceFirst ~server "stomp" "tcp")))
                    (.startsWith ~server "stomp+ssl:")
                      (doto (StompJmsConnectionFactory.)
                        (.setSslContext (get-adjusted-ssl-context))
                        (.setBrokerURI (.replaceFirst ~server "stomp\\+ssl" "ssl")))
                    :default (ActiveMQConnectionFactory. ~server))
         ~'connection (doto (if (and (not (nil? *user-name*)) (not (nil? *user-password*)))
                              (do
                                (println "Creating connection for user:" *user-name*)
                                (.createConnection factory# *user-name* *user-password*))
                              (.createConnection factory#))
                        (.start))
         ~'session (.createSession ~'connection false Session/AUTO_ACKNOWLEDGE)
         split-endpoint# (filter #(not= % "") (split ~endpoint-description #"/"))
         endpoint-type# (first split-endpoint#)
         endpoint-name# (join "/" (rest split-endpoint#))
         _# (println "Creating endpoint. Type:" endpoint-type# "Name:" endpoint-name#)
         ~'endpoint (condp = endpoint-type#
                      "topic" (.createTopic ~'session endpoint-name#)
                      "queue" (.createQueue ~'session endpoint-name#)
                      (println "Could not create endpoint. Type:" endpoint-type# "Name:" endpoint-name#))]
     ~@body))

(defn init-topic [server topic-name]
  (with-endpoint server topic-name
    (.close connection)
    endpoint))

(defn create-producer [server endpoint-description]
  (println "Creating producer for endpoint description:" endpoint-description)
  (with-endpoint server endpoint-description
    (let [^MessageProducer producer (doto
                                      (.createProducer session endpoint)
                                      (.setDeliveryMode DeliveryMode/NON_PERSISTENT))]
      (fn [o]
        (cond
          (= :close o) (.close connection)
          :default (cond
                     (= (type o) byte-array-type) (.send producer (doto ^BytesMessage (.createBytesMessage ^Session session) (.writeBytes ^bytes o)))
                     (= (type o) java.lang.String) (.send producer ^TextMessage (.createTextMessage ^Session session ^java.lang.String o))
                     :default (.send producer (.createObjectMessage ^Session session o))))))))

(defn create-pooled-bytes-message-producer [server endpoint-description pool-size]
  (println "Creating producer for endpoint description:" endpoint-description)
  (with-endpoint server endpoint-description
    (let [^MessageProducer producer (doto
                                      (.createProducer session endpoint)
                                      (.setDeliveryMode DeliveryMode/NON_PERSISTENT))]
      (PooledBytesMessageProducer. producer session connection pool-size))))

(defn close [s]
  (s :close))

(defn create-pooled-producer [server endpoint-description pool-size]
  (let [producer (create-producer server endpoint-description)
        pool (ref [])
        pool-fn (fn [data]
                  (dosync 
                    (alter pool #(conj % data))
                    (when (>= (count @pool) pool-size)
                      (producer @pool)
                      (ref-set pool []))))]
    (fn [o]
      (cond
        (= :close o) (close producer)
        :default (pool-fn o)))))

(defn create-pooled-arraylist-producer [server endpoint-description pool-size]
  (let [producer (create-producer server endpoint-description)
        pool (ArrayList. pool-size)]
    (fn [o]
      (cond
        (= :close o) (producer :close)
        :default (do
                   (.add pool o)
                   (when (>= (.size pool) pool-size)
                     (producer pool)
                     (.clear pool)))))))

(defn create-pooled-arraylist-drainto-producer [server endpoint-description pool-size]
  (let [producer (create-producer server endpoint-description)
        pool (ArrayList. pool-size)]
    (fn [o]
      (cond
        (= :close o) (producer :close)
        :default (do
                   (.drainTo ^ArrayBlockingQueue o pool pool-size)
                   (producer pool)
                   (.clear pool))))))

(defn create-pooled-arraylist-kryo-producer
  ([server endpoint-description pool-size]
    (create-pooled-arraylist-kryo-producer
      server endpoint-description pool-size (fn [^bytes ba] ba)))
  ([server endpoint-description pool-size ba-out-fn]
    (let [producer (create-producer server endpoint-description)
          pool (ArrayList. pool-size)
          out (Output. *kryo-output-size*)
          kryo (Kryo.)]
      (fn [o]
        (cond
          (= :close o) (producer :close)
          :default (do
                     (.add pool o)
                     (when (>= (.size pool) pool-size)
                       (let [obj (.writeObject kryo out pool)
                             ^bytes b-array (ba-out-fn (.toBytes out))]
                         (producer b-array)
                         (.clear out)
                         (.clear pool)))))))))

(defn create-pooled-arraylist-kryo-lzf-producer [server endpoint-description pool-size]
  (create-pooled-arraylist-kryo-producer
    server endpoint-description pool-size (fn [^bytes ba] (LZFEncoder/encode ba))))

(defn create-consumer [server endpoint-description cb]
  (println "Creating consumer for endpoint descriptiont:" endpoint-description)
  (with-endpoint server endpoint-description
    (let [listener (proxy [MessageListener] []
            (onMessage [^Message m] (cond
                                      (instance? BytesMessage m) (let [data (byte-array (.getBodyLength ^BytesMessage m))]
                                                                   (.readBytes ^BytesMessage m data)
                                                                   (cb data))
                                      (instance? ObjectMessage m)  (cb (.getObject ^ObjectMessage m))
                                      (instance? TextMessage m) (cb (.getText ^TextMessage m))
                                      :default (println "Unknown message type:" (type m)))))
          consumer (doto
                     (.createConsumer session endpoint)
                     (.setMessageListener listener))]      
      (fn [k]
        (cond
          (= :close k) (do
                         (println "Closing consumer for endpoint:" endpoint)
                         (.close connection)))))))

(defn create-lzf-consumer [server endpoint-description cb]
  (create-consumer
    server endpoint-description (fn [^bytes ba] (cb (LZFDecoder/decode ba)))))

(defn create-kryo-consumer
  ([server endpoint-description cb]
    (create-kryo-consumer
      server endpoint-description cb (fn [^bytes ba] ba)))
  ([server endpoint-description cb ba-in-fn]
    (println "Creating consumer for endpoint description:" endpoint-description)
    (with-endpoint server endpoint-description
      (let [kryo (Kryo.)
            in (Input.)
            listener (proxy [MessageListener] []
              (onMessage [^Message m] (cond
                                        (instance? ObjectMessage m)  (cb (.getObject ^ObjectMessage m))
                                        (instance? BytesMessage m) (let [data (byte-array (.getBodyLength ^BytesMessage m))]
                                                                     (.readBytes ^BytesMessage m data)
                                                                     (.setBuffer in (ba-in-fn data))
                                                                     (cb (.readObject kryo in ArrayList)))
                                        :default (println "Unknown message type:" (type m)))))
            consumer (doto
                       (.createConsumer session endpoint)
                       (.setMessageListener listener))]      
        (fn [k]
          (cond
            (= :close k) (do
                           (println "Closing consumer for endpoint:" endpoint)
                           (.close connection))))))))

(defn create-kryo-lzf-consumer [server endpoint-description cb]
  (create-kryo-consumer
    server endpoint-description cb (fn [^bytes ba] (LZFDecoder/decode ba))))

