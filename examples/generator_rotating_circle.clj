; Run via, e.g.:
; java -jar dist/bowerick-2.7.5-standalone.jar -G custom-fn -X examples/generator_rotating_circle.clj -I 20 -D /topic/aframe -u "ws://127.0.0.1:1864"
(fn [producer delay-fn]
  (let [max_angle (* 2.0 Math/PI)
        angle_increment (/ max_angle 100.0)
        circle_steps (range 0.0 max_angle angle_increment)
        circle_coordinates (mapv (fn [angle]
                                   (let [x (Math/cos angle)
                                         y (Math/sin angle)]
                                     {"x" x, "y" y, "z" 0.0}))
                                 circle_steps)]
    (fn []
      (loop [rotation_angle 0.0]
        (let [rotated_coordinates (mapv (fn [coords]
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
                                              (coords "x"))))
                                        circle_coordinates)]
          (producer rotated_coordinates)
          (delay-fn)
          (if (> rotation_angle max_angle)
            (recur (+ (- rotation_angle max_angle) angle_increment))
            (recur (+ rotation_angle angle_increment))))))))
