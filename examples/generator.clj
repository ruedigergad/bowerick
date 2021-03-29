(fn generator
  [producer delay-fn]
  (let [max_angle (* 2.0 Math/PI)
        angle_increment (/ max_angle 100.0)]
    (fn []
      (loop [angle 0.0]
        (let [x (Math/cos angle)
              y (Math/sin angle) ]
          (producer {"x" x, "y" y, "z" 0.0})
          (delay-fn)
          (if (> angle max_angle)
            (recur (+ (- angle max_angle) angle_increment))
            (recur (+ angle angle_increment))))))))
