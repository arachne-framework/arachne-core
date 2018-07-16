(ns arachne.run-test
  (:require #_[arachne.run :as run]
            [clojure.test :refer :all]))

#_(deftest run-app
  (println "loading...")
  (let [rt (run/-main "\"<http://example.com/test/test-app>\""
                      "\"<http://example.com/test/rt>\"")]
    (println "loaded...")
    (println (into {} (-> rt :system)))
    (println (class (:system rt)))
    (is (-> rt :system :test/component :started))

    ))
