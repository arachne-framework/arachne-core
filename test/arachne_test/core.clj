(ns arachne-test.core
  (:require [com.stuartsierra.component :as c]))

(defrecord TestComponent []
  c/Lifecycle
  (start [this]
    (println "Starting test component!")
    this)
  (stop [this]
    (println "Stopping test component!")
    this))

(defn test-component
  "Constructor for a test component"
  []
  (->TestComponent))
