(ns arachne.core.ns-config-test
  (:require [clojure.test :refer :all]
            [arachne.core :as core]
            [arachne.core.runtime :as rt]
            [arachne.core.config :as cfg]))

(arachne.error/explain-test-errors!)

(deftest ns-config-test
  (let [cfg-a (core/build-config '[:org.arachne-framework/arachne-core]
              'arachne.core.ns-config-test.script)
        cfg-b (core/build-config '[:org.arachne-framework/arachne-core]
              'arachne.core.ns-config-test.script)]
    (doseq [cfg [cfg-a cfg-b]]
      (is (cfg/q cfg '[:find ?rt .
                       :where
                       [?rt :arachne.runtime/components ?a]
                       [?a :arachne/id :test/a]
                       [?a :arachne.component/dependencies ?ad]
                       [?ad :arachne.component.dependency/entity ?b]
                       [?b :arachne/id :test/b]])))))

