;;;
;;;   Copyright 2015, University of Applied Sciences Frankfurt am Main
;;;
;;;   This software is released under the terms of the Eclipse Public License 
;;;   (EPL) 1.0. You can find a copy of the EPL at: 
;;;   http://opensource.org/licenses/eclipse-1.0.php
;;;

(ns bowerick.test.jms-permissions-test
  ^{:author "Ruediger Gad",
    :doc "Tests for JMS permissions"}  
  (:require
    [bowerick.jms :as jms]
    [bowerick.test.test-helper :as th]
    [clojure.test :as test])
  (:import
    (org.apache.activemq.broker BrokerService)
    (org.apache.activemq.security AuthenticationUser AuthorizationEntry AuthorizationPlugin DefaultAuthorizationMap SimpleAuthenticationPlugin)))

(def jms-server-addr "tcp://127.0.0.1:31313")
(def test-user-name "test-user")
(def test-user-password "secret")
(def test-user-groups "test-group,admins,publishers,consumers")
(def test-users [(AuthenticationUser. test-user-name test-user-password test-user-groups)])
(def test-topic "/topic/test.topic.a")

(test/deftest create-producer-without-permission-test
  (let [authentication-plugin (doto (SimpleAuthenticationPlugin. test-users)
                                (.setAnonymousAccessAllowed false)
                                (.setAnonymousUser "anonymous")
                                (.setAnonymousGroup "anonymous"))
        default-authorization-entry (doto (AuthorizationEntry.)
                                      (.setAdmin "admins")
                                      (.setRead "consumers")
                                      (.setWrite "publishers")
                                      (.setTopic ">"))
        default-authorization-map (doto (DefaultAuthorizationMap.)
                                    (.setAuthorizationEntries [default-authorization-entry]))
        authorization-plugin (AuthorizationPlugin. default-authorization-map)
        broker-service (doto (BrokerService.)
                         (.addConnector jms-server-addr)
                         (.setPersistent false)
                         (.setUseJmx false)
                         (.setPlugins (into-array org.apache.activemq.broker.BrokerPlugin [authentication-plugin authorization-plugin]))
                         (.start))]
    (test/is
      (thrown-with-msg?
        javax.jms.JMSSecurityException
        #"User name .* or password is invalid."
        (jms/create-single-producer jms-server-addr test-topic)))
    (.stop broker-service)))

(test/deftest create-producer-with-permission-test
  (let [authentication-plugin (doto (SimpleAuthenticationPlugin. test-users)
                                (.setAnonymousAccessAllowed false)
                                (.setAnonymousUser "anonymous")
                                (.setAnonymousGroup "anonymous"))
        default-authorization-entry (doto (AuthorizationEntry.)
                                      (.setAdmin "admins")
                                      (.setRead "consumers")
                                      (.setWrite "publishers")
                                      (.setTopic ">"))
        default-authorization-map (doto (DefaultAuthorizationMap.)
                                    (.setAuthorizationEntries [default-authorization-entry]))
        authorization-plugin (AuthorizationPlugin. default-authorization-map)
        broker-service (doto (BrokerService.)
                         (.addConnector jms-server-addr)
                         (.setPersistent false)
                         (.setUseJmx false)
                         (.setPlugins (into-array org.apache.activemq.broker.BrokerPlugin [authentication-plugin authorization-plugin]))
                         (.start))]
    (binding
      [jms/*user-name* test-user-name
       jms/*user-password* test-user-password]
      (jms/create-single-producer jms-server-addr test-topic))
    (.stop broker-service)))

(test/deftest test-selective-permissions-with-anonymous-test
  (let [authentication-plugin (doto (SimpleAuthenticationPlugin. test-users)
                                (.setAnonymousAccessAllowed true)
                                (.setAnonymousUser "anonymous")
                                (.setAnonymousGroup "anonymous"))
        default-authorization-entry (doto (AuthorizationEntry.)
                                      (.setAdmin "admins")
                                      (.setRead "consumers")
                                      (.setWrite "publishers")
                                      (.setTopic ">"))
        authorization-entry-anon-write (doto (AuthorizationEntry.)
                                        (.setWrite "anonymous")
                                        (.setTopic "test.topic.a"))
        authorization-entry-anon-read (doto (AuthorizationEntry.)
                                        (.setRead "anonymous")
                                        (.setTopic "ActiveMQ.Advisory.TempQueue,ActiveMQ.Advisory.TempTopic"))
        default-authorization-map (doto (DefaultAuthorizationMap.)
                                    (.setAuthorizationEntries [default-authorization-entry authorization-entry-anon-write authorization-entry-anon-read]))
        authorization-plugin (AuthorizationPlugin. default-authorization-map)
        broker-service (doto (BrokerService.)
                         (.addConnector jms-server-addr)
                         (.setPersistent false)
                         (.setUseJmx false)
                         (.setPlugins (into-array org.apache.activemq.broker.BrokerPlugin [authentication-plugin authorization-plugin]))
                         (.start))]
    (binding
      [jms/*user-name* test-user-name
       jms/*user-password* test-user-password]
      (jms/create-single-producer jms-server-addr test-topic))
    (binding
      [jms/*user-name* test-user-name
       jms/*user-password* test-user-password]
      (jms/create-single-consumer jms-server-addr test-topic (fn [_])))
    (jms/create-single-producer jms-server-addr test-topic)
    (test/is
      (thrown-with-msg?
        javax.jms.JMSSecurityException
        #"User anonymous is not authorized to read from: topic://test.topic.a"
        (jms/create-single-consumer jms-server-addr test-topic (fn [_]))))
    (.stop broker-service)))

(test/deftest test-selective-permissions-with-anonymous-convenience-test
  (let [authorization-rules [{"target" "/topic/>", "admin" "admins", "read" "consumers", "write" "publishers"}
                             {"target" "/topic/test.topic.a", "write" "anonymous"}
                             {"target" "/topic/ActiveMQ.Advisory.TempQueue,ActiveMQ.Advisory.TempTopic", "read" "anonymous"}]
        broker-service (th/start-test-broker
                         jms-server-addr
                         true
                         [{"name" "test-user", "password" "secret", "groups" "test-group,admins,publishers,consumers"}] 
                         authorization-rules)]
    (binding
      [jms/*user-name* test-user-name
       jms/*user-password* test-user-password]
      (jms/create-single-producer jms-server-addr test-topic))
    (binding
      [jms/*user-name* test-user-name
       jms/*user-password* test-user-password]
      (jms/create-single-consumer jms-server-addr test-topic (fn [_])))
    (jms/create-single-producer jms-server-addr test-topic)
    (test/is
      (thrown-with-msg?
        javax.jms.JMSSecurityException
        #"User anonymous is not authorized to read from: topic://test.topic.a"
        (jms/create-single-consumer jms-server-addr test-topic (fn [_]))))
    (jms/stop broker-service)))
