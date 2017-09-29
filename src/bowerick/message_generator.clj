;;;
;;;   Copyright 2017, Ruediger Gad
;;;
;;;   This software is released under the terms of the Eclipse Public License 
;;;   (EPL) 1.0. You can find a copy of the EPL at: 
;;;   http://opensource.org/licenses/eclipse-1.0.php
;;;

(ns
  ^{:author "Ruediger Gad",
    :doc "Functions for generating messages"} 
  bowerick.message-generator
  (:require
    [clojure.java.io :as java-io]
    [clojure.string :as str]
    [clj-assorted-utils.util :as utils]))

(defn txt-file-generator [producer delay-fn in-path split-regex]
  (fn []
    (doseq [l (str/split (slurp in-path) split-regex)]
      (producer l)
      (delay-fn))))

(defn txt-file-line-generator [producer delay-fn in-path]
  (fn []
    (with-open [rdr (java-io/reader in-path)]
      (doseq [l (line-seq rdr)]
        (producer l)
        (delay-fn)))))

