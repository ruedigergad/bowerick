(fn [producer delay-fn]
  (fn []
    (producer "producer return value")))
