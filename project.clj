;(defproject bowerick "3.0.1-SNAPSHOT"
(defproject bowerick "3.0.0"
  :description "Easing Simple JMS Tasks with Clojure (and Java)"
  :dependencies [[com.twitter/carbonite "1.5.0"]
                 [cheshire "5.13.0"]
                 [cli4clj "1.9.0"]
                 [clj-assorted-utils "1.19.0"]
                 [com.ning/compress-lzf "1.1.2"]
                 [com.taoensso/nippy "3.4.2"]
                 [jakarta.websocket/jakarta.websocket-client-api "2.2.0"]
                 [org.apache.activemq/activemq-broker "6.1.3"]
                 [org.apache.activemq/activemq-client "6.1.3"]
                 [org.apache.activemq/activemq-http "6.1.3" :exclusions [org.eclipse.jetty/jetty-server org.eclipse.jetty/jetty-xml org.eclipse.jetty/jetty-webapp]]
                 [org.apache.activemq/activemq-jaas "6.1.3"]
                 [org.apache.activemq/activemq-openwire-legacy "6.1.3"]
                 [org.apache.activemq/activemq-stomp "6.1.3"]
                 [org.clojure/clojure "1.12.0"]
                 [org.clojure/tools.cli "1.1.230"]
                 [org.eclipse.jetty/jetty-server "11.0.24"]
                 [org.eclipse.jetty/jetty-xml "11.0.24"]
                 [org.eclipse.jetty/jetty-webapp "11.0.24"]
                 [org.eclipse.jetty.websocket/websocket-jetty-server "11.0.24"]
                 [org.eclipse.paho/org.eclipse.paho.client.mqttv3 "1.2.5"]
                 [org.glassfish.tyrus/tyrus-client "2.2.0"]
                 [org.glassfish.tyrus/tyrus-container-jdk-client "2.2.0"]
                 [org.iq80.snappy/snappy "0.5"]
                 [org.springframework/spring-messaging "6.1.14"]
                 [org.springframework/spring-websocket "6.1.14"]
                 [org.slf4j/slf4j-simple "2.0.16"]
                 [rgad/stompjms-client "1.22"]
                 [juxt/dirwatch "0.2.5"]
                 [org.tcrawley/dynapath "1.1.0"]
                 [spootnik/signal "0.2.5"]]
  :license {:name "Eclipse Public License (EPL) - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo
            :comments "This is the same license as used for Clojure."}
  :global-vars {*warn-on-reflection* true}
  :java-source-paths ["test/data"]
  :prep-tasks [["compile" "bowerick.java-interfaces"]
               ["compile" "bowerick.JmsController"]
               "javac"
               "compile"]
  :main bowerick.main
  :aot :all
  :test-selectors {:default (complement :benchmark)
                   :benchmark :benchmark}
  :test2junit-output-dir "docs/test-results"
  :test2junit-run-ant true
  :html5-docs-docs-dir "docs/doc"
  :html5-docs-ns-includes #"^bowerick.*"
  :html5-docs-repository-url "https://github.com/ruedigergad/bowerick/blob/master"
  :profiles {:repl
               {:dependencies  [[jonase/eastwood "1.4.3" :exclusions  [org.clojure/clojure]]]}
             :test
               {:dependencies [[criterium "0.4.6"]]
                :test-paths ["test" "benchmark"]}}
  :plugins [[lein-cloverage "1.2.4"] [lein-with-env-vars "0.2.0"] [test2junit "1.4.2"] [lein-html5-docs "3.0.3"]]
  ; Explicitly forcing TLSv1.2, for now, because of: https://bugs.openjdk.java.net/browse/JDK-8211426
  ;:jvm-opts ["-Dorg.slf4j.simpleLogger.defaultLogLevel=trace"]
  ;:jvm-opts ["-Djavax.net.debug=all" "-Djdk.tls.server.protocols=TLSv1.2" "-Djdk.tls.client.protocols=TLSv1.2"]
  ;:jvm-opts ["-Djavax.net.debug=all"]
  :jvm-opts ["--add-opens" "java.base/sun.security.tools.keytool=ALL-UNNAMED"]
  :env-vars {:BOWERICK_ADMIN_PASS "test_admin_pass"
             :BOWERICK_WRITE_PASS "test_write_pass"
             :BOWERICK_READ_PASS "test_read_pass"}
  :hooks [leiningen.with-env-vars/auto-inject]
  )
