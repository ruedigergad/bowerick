(fn [_ delay-fn]
  (fn []
    (delay-fn "delay-fn return value")))
