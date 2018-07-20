(ns arachne.run-test
  (:require [arachne.run :as run]
            [clojure.test :refer :all]))

(deftest run-app
  (let [rt (run/-main ":test/test-app" ":test/rt")]
    (is (-> rt :system :test/component :started))))
