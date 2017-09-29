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
    [clj-assorted-utils.util :as utils])
  (:import
    (java.io File FileInputStream)
    (java.nio ByteOrder MappedByteBuffer)
    (java.nio.channels FileChannel FileChannel$MapMode)))

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

(defn binary-file-generator [producer delay-fn in-path initial-offset length-field-offset length-field-size header-size]
  (fn []
    (let [^File in-file (File. in-path)
          file-length (.length in-file)]
      (with-open [^FileInputStream in-stream (FileInputStream. in-file)]
        (let [^FileChannel file-channel (.getChannel in-stream)
              ^MappedByteBuffer buffer (.map file-channel FileChannel$MapMode/READ_ONLY 0 file-length)]
          (.order buffer ByteOrder/LITTLE_ENDIAN)
          (loop [offset initial-offset]
            (let [data-size (.getInt buffer (+ offset length-field-offset))
                  total-size (+ data-size header-size)
                  ba (byte-array total-size)]
              (.position buffer offset)
              (.get buffer ba 0 total-size)
              (producer ba)
              (delay-fn)
              (if (< (+ offset total-size) file-length)
                (recur (+ offset total-size))))))))))

