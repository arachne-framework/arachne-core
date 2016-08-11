(ns arachne.core.dsl-test
  (:require [clojure.test :refer :all]
            [arachne.core :as core]
            [arachne.core.runtime :as rt]
            [arachne.core.config :as cfg]
            [com.stuartsierra.component :as component]))

(defrecord TestComponent [running?]
  component/Lifecycle
  (start [this] (assoc this :running? true))
  (stop [this] (assoc this :running? false)))

(defn test-ctor [_ _]
  (->TestComponent false))

(deftest basic-system
  (let [cfg (core/build-config '[:org.arachne-framework/arachne-core]
              '(do (require '[arachne.core.dsl :as dsl])
                   (dsl/runtime :test/rt [:test/a])
                   (dsl/component :test/a {:test/b :b, :test/c :c}
                     'arachne.core.dsl-test/test-ctor)
                   (dsl/component :test/b {:test/c :c}
                     'arachne.core.dsl-test/test-ctor)
                   (dsl/component :test/c 'arachne.core.dsl-test/test-ctor)))
        rt (component/start (rt/init cfg [:arachne/id :test/rt]))]

    (is (rt/lookup rt [:arachne/id :test/a]))
    (is (rt/lookup rt [:arachne/id :test/b]))
    (is (rt/lookup rt [:arachne/id :test/c]))

    (is (:running? (rt/lookup rt [:arachne/id :test/a])))
    (is (:running? (rt/lookup rt [:arachne/id :test/b])))
    (is (:running? (rt/lookup rt [:arachne/id :test/c])))

    (is (= (rt/lookup rt [:arachne/id :test/b])
          (:b (rt/lookup rt [:arachne/id :test/a]))))

    (is (= (rt/lookup rt [:arachne/id :test/c])
          (:c (rt/lookup rt [:arachne/id :test/a]))))

    (is (= (rt/lookup rt [:arachne/id :test/c])
          (:c (rt/lookup rt [:arachne/id :test/b]))))

    (component/stop rt)))
