(fn [producer delay-fn]
  (fn []
    (delay-fn "delay-fn return value")))
