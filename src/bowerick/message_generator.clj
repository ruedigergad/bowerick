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
    [bowerick.jms :refer (to-json-bytes)]
    [clojure.java.io :as java-io]
    [clojure.string :as str]
    [clj-assorted-utils.util :as utils]
    [dynapath.util :as dp]
    [juxt.dirwatch :refer (watch-dir)])
  (:import
    (bowerick JmsProducer)
    (clojure.lang DynamicClassLoader)
    (java.io File FileInputStream)
    (java.lang Math)
    (java.nio ByteOrder MappedByteBuffer)
    (java.nio.channels FileChannel FileChannel$MapMode)))

(defn create-message-generator
  [producer delay-fn generator-name generator-args-string]
  (let [generator-construction-fn (ns-resolve
                                    'bowerick.message-generator
                                    (symbol (str generator-name "-generator")))
        tmp-args (binding [*read-eval* false]
                   (try
                     (read-string generator-args-string)
                     (catch Exception e
                       generator-args-string)))
        generator-args (if (vector? tmp-args)
                         tmp-args
                         (if (symbol? tmp-args)
                           [(str tmp-args)]
                           [tmp-args]))]
    (if (not (nil? generator-construction-fn))
      (apply generator-construction-fn  producer delay-fn generator-args))))

(defn txt-file-generator
  [producer delay-fn in-path split-regex]
  (let [lines (str/split (slurp in-path) split-regex)]
    (fn []
      (doseq [l lines]
        (producer l)
        (delay-fn)))))

(defn txt-file-line-generator
  [producer delay-fn in-path]
  (fn []
    (with-open [rdr (java-io/reader in-path)]
      (doseq [l (line-seq rdr)]
        (producer l)
        (delay-fn)))))

(defn binary-file-generator
  [producer delay-fn in-path initial-offset length-field-offset length-field-size header-size]
  (let [^File in-file (File. in-path)
        file-length (.length in-file)
        ^MappedByteBuffer buffer (with-open [^FileInputStream in-stream (FileInputStream. in-file)]
                                   (let [^FileChannel file-channel (.getChannel in-stream)
                                         ^MappedByteBuffer mbb (.map file-channel FileChannel$MapMode/READ_ONLY 0 file-length)]
                                     (.order mbb ByteOrder/LITTLE_ENDIAN)
                                     mbb))]
    (fn []
      (.rewind buffer)
      (loop [offset initial-offset]
        (let [data-size (.getInt buffer (+ offset length-field-offset))
              total-size (+ data-size header-size)
              ba (byte-array total-size)]
          (.position buffer offset)
          (.get buffer ba 0 total-size)
          (producer ba)
          (delay-fn)
          (if (< (+ offset total-size) file-length)
            (recur (+ offset total-size))))))))

(defn pcap-file-generator
  [producer delay-fn in-path]
  (binary-file-generator producer delay-fn in-path 24 8 4 16))

(defn heart4family-generator
  [producer delay-fn _]
  (let [increment 0.075]
    (fn []
      (loop [t (- Math/PI)]
        (let [x (* 0.15 (* 16.0 (Math/pow (Math/sin t) 3.0)))
              y (+ 1.2
                   (* 0.15
                      (- (* 13.0 (Math/cos t))
                      (* 5.0 (Math/cos (* 2.0 t)))
                      (* 2.0 (Math/cos (* 3.0 t)))
                      (Math/cos (* 4.0 t)))))]
          (producer (to-json-bytes {"x" x, "y" y, "z" 0.0}))
          (delay-fn)
          (if (> t Math/PI)
            (recur (+ (- t (* 2.0 Math/PI)) increment))
            (recur (+ t increment))))))))

(defn custom-fn-generator
  [producer delay-fn in-path]
  (let [producer-fn (atom (fn []))
        read-fn (fn [event]
                  (when (or
                          (nil? event)
                          (and
                            (= 1 (:count event))
                            (= :modify (:action event))
                            (= in-path (-> event :file .getPath))))
                    (println "Loading custom-fn-generator:" in-path)
                    (if (str/ends-with? in-path ".class")
                      (let [_ (println "Loading from Java Class file.")
                            in-file (java-io/file in-path)
                            in-dir-url (-> in-file .getParentFile java-io/as-url)
                            classname (-> in-file .getName (str/split #".class") first)
                            classloader (DynamicClassLoader.)
                            _ (dp/add-classpath-url classloader in-dir-url)
                            msg-gen-class (.loadClass classloader classname)
                            msg-gen-constructor (-> msg-gen-class .getDeclaredConstructors (aget 0))
                            _ (.setAccessible msg-gen-constructor true)
                            msg-gen-instance (.newInstance msg-gen-constructor nil)
                            jms-producer (proxy [JmsProducer] []
                                           (sendData [data & _] (producer data) (delay-fn)))
                            prod-fn (fn [] (.generateMessage msg-gen-instance jms-producer))]
                        (reset! producer-fn prod-fn))
                      (let [_ (println "Loading from Clojure file.")
                            gen-fn (-> (slurp in-path) read-string eval)
                            prod-fn (gen-fn producer delay-fn)]
                        (reset! producer-fn prod-fn)))))]
    (watch-dir read-fn (.getParentFile (java-io/file in-path)))
    (read-fn nil)
    (fn [] (@producer-fn))))

(defn hello-world-generator
  [producer delay-fn _]
  (fn []
    (producer "hello world")
    (delay-fn)))

(defn yin-yang-generator
  [producer delay-fn _]
  (let [max_angle (* 2.0 Math/PI)
        angle_increment (/ max_angle 100.0)
        circle_steps (range 0.0 max_angle angle_increment)
        half_circle_steps (range 0.0 Math/PI angle_increment)
        circle_coordinates (mapv (fn [angle]
                                   (let [x (Math/cos angle)
                                         y (Math/sin angle)]
                                     {"x" x, "y" y, "z" 0.0,
                                      "scale_x" 0.2, "scale_y" 0.2, "scale_z" 0.01,
                                      "rotation_x" 0.0, "rotation_y", 0.0, "rotation_z" 0.0}))
                                 circle_steps)
        upper_half_circle (mapv (fn [angle]
                                  (let [x (/ (Math/sin angle) 2.0)
                                        y (+ (/ (Math/cos angle) 2.0) 0.5)]
                                    {"x" x, "y" y, "z" 0.0,
                                     "scale_x" 0.2, "scale_y" 0.2, "scale_z" 0.01,
                                     "rotation_x" 0.0, "rotation_y", 0.0, "rotation_z" 0.0}))
                                half_circle_steps)
        lower_half_circle (mapv (fn [angle]
                                  (let [x (/ (Math/sin angle) -2.0)
                                        y (- (/ (Math/cos angle) 2.0) 0.5)]
                                    {"x" x, "y" y, "z" 0.0,
                                     "scale_x" 0.2, "scale_y" 0.2, "scale_z" 0.01,
                                     "rotation_x" 0.0, "rotation_y", 0.0, "rotation_z" 0.0}))
                                half_circle_steps)
        dots_coordinates [{"x" 0.0, "y" 0.5, "z" 0.0,
                           "scale_x" 0.5, "scale_y" 0.5, "scale_z" 0.01,
                           "rotation_x" 0.0, "rotation_y", 0.0, "rotation_z" 0.0}
                          {"x" 0.0, "y" -0.5, "z" 0.0,
                           "scale_x" 0.5, "scale_y" 0.5, "scale_z" 0.01,
                           "rotation_x" 0.0, "rotation_y", 0.0, "rotation_z" 0.0}]
        coordinates (concat circle_coordinates
                            upper_half_circle
                            lower_half_circle
                            dots_coordinates)
        colored_coordinates (mapv (fn [coords]
                                    (let [color_ref (* Math/PI (+ 1.0 (coords "y")))]
                                      (->
                                        coords
                                        (assoc "color_r" (-> (Math/cos color_ref) (+ 0.4) (max 0.0) (min 1.0)))
                                        (assoc "color_g" (-> (/ max_angle 3.0) (+ color_ref) (Math/cos) (+ 0.4) (max 0.0) (min 1.0)))
                                        (assoc "color_b" (-> (/ max_angle 3.0) (* 2.0) (+ color_ref) (Math/cos) (+ 0.4) (max 0.0) (min 1.0))))))
                                  coordinates)
        rot_angle (atom 0.0)]
    (fn []
      (let [rotation_angle @rot_angle
            rotated_coordinates (mapv (fn [coords]
                                        (->
                                          coords
                                          (update-in
                                            ["x"]
                                            (fn [x z]
                                              (- (* (Math/cos rotation_angle) x)
                                                 (* (Math/sin rotation_angle) z)))
                                            (coords "z"))
                                          (update-in
                                            ["z"]
                                            (fn [z x]
                                              (+ (* (Math/sin rotation_angle) x)
                                                 (* (Math/cos rotation_angle) z)))
                                            (coords "x"))
                                          (update-in
                                            ["rotation_y"]
                                            (fn [_]
                                              (- rotation_angle)))))
                                      colored_coordinates)]
        (producer (to-json-bytes rotated_coordinates))
        (delay-fn)
        (if (> rotation_angle max_angle)
          (reset! rot_angle (+ (- rotation_angle max_angle) angle_increment))
          (reset! rot_angle (+ rotation_angle angle_increment)))))))

