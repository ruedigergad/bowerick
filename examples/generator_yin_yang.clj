; Run via, e.g.:
; java -jar dist/bowerick-2.7.5-standalone.jar -G custom-fn -X examples/generator_yin_yang.clj -I 20 -D /topic/aframe -u "ws://127.0.0.1:1864"
(fn [producer delay-fn]
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
        (producer rotated_coordinates)
        (delay-fn)
        (if (> rotation_angle max_angle)
          (reset! rot_angle (+ (- rotation_angle max_angle) angle_increment))
          (reset! rot_angle (+ rotation_angle angle_increment)))))))

