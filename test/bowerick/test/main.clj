;;;
;;;   Copyright 2021 Ruediger Gad
;;;
;;;   This software is released under the terms of the Eclipse Public License 
;;;   (EPL) 1.0. You can find a copy of the EPL at: 
;;;   http://opensource.org/licenses/eclipse-1.0.php
;;;

(ns 
  ^{:author "Ruediger Gad",
    :doc "Tests for aspects regarding the main entrypoint that are not covered elsewhere, e.g., in the client-* tests."}  
  bowerick.test.main
  (:require
    [bowerick.main :as main]
    [cli4clj.cli-tests :as cli-test]
    [clojure.string :as str]
    [clojure.test :as test]))



(test/deftest print-license-information-test
  (let [out-string (cli-test/test-cli-stdout #(main/run-cli-app "--license-information") [])]
    (test/is
      (=
        "Bowerick is licensed under the terms of the Eclipse Public License (EPL) 1.0."
        (-> out-string (str/split #"\n") first)))  
    (test/is
      (=
        "xpp3/xpp3_min                                              Indiana University Extreme! Lab Software License, vesion 1.1.1"
        (-> out-string (str/split #"\n") last)))))

(test/deftest print-license-information-long-test
  (let [out-string (cli-test/test-cli-stdout #(main/run-cli-app "--license-information-long") [])]
    (test/is
      (=
        "Bowerick is licensed under the terms of the Eclipse Public License (EPL) 1.0."
        (-> out-string (str/split #"\n") first)))  
    (test/is
      (=
        "from your version."
        (-> out-string (str/split #"\n") last)))))
