(ns arachne.error.test-runner
  (:require [cognitect.test-runner]
            [arachne.error :as e]))

(defn -main
  "Run tests using cognitect/test-runner, enabling explain-test-errors. Args are the same as for cognitect.test-runner/-main."
  [& args]
  (e/explain-test-errors!)
  (apply cognitect.test-runner/-main args))
