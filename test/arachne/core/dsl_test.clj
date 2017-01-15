(ns arachne.core.dsl-test
  (:require [clojure.test :refer :all]
            [arachne.core :as core]
            [arachne.core.runtime :as rt]
            [arachne.core.config :as cfg]
            [com.stuartsierra.component :as component]
            [arachne.core.dsl :as a]))

(defrecord TestComponent [running?]
  component/Lifecycle
  (start [this] (assoc this :running? true))
  (stop [this] (assoc this :running? false)))

(defn test-ctor [_ _]
  (->TestComponent false))

(defn basic-system-cfg []

  (a/runtime :test/rt [:test/a])

  (a/component :test/a `test-ctor {:b :test/b,
                                   :c :test/c})

  (a/component :test/b `test-ctor {:c :test/c})

  (a/component :test/c `test-ctor))

(deftest basic-system
  (let [cfg (core/build-config '[:org.arachne-framework/arachne-core] `(basic-system-cfg))
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

(defn missing-runtime-cfg []

  (a/runtime :test/rt [:test/a])
  (a/component :test/a `test-ctor))

(deftest missing-runtime
  (let [cfg (core/build-config '[:org.arachne-framework/arachne-core]
              `(missing-runtime-cfg))]

    (is (thrown-with-msg? arachne.ArachneException #"no-such-runtime"
          (component/start (rt/init cfg [:arachne/id :test/no-such-runtime]))))))