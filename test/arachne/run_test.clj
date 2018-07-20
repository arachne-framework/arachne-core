(ns arachne.run-test
  (:require [arachne.run :as run]
            [clojure.test :refer :all]))

(deftest run-app
  (let [rt (run/-main "\"<http://example.com/test/test-app>\""
                      "\"<http://example.com/test/rt>\"")]
    (is (-> rt :system (get "<http://example.com/test/component>") :started))

    ))
