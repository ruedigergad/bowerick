(fn [producer delay-fn]
  (fn []
    (producer "Hello custom-fn generator!")
    (delay-fn)))
