(ns test-app.core
  (:require [com.stuartsierra.component :as c]
            [arachne.log :as log]))

(defrecord TestComponent [started]
  c/Lifecycle
  (start [this]
    (log/info :msg "Starting test component!")
    (assoc this :started true))
  (stop [this]
    (log/info :msg "Stopping test component!")
    (assoc this :started false)))

(defn test-component
  "Constructor for a test component"
  []
  (->TestComponent false))
