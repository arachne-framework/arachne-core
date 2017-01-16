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

(defn basic-system-cfg-nested []

  ;; Alternate syntax for anonymous components
  (a/runtime :test/rt [(a/component `test-ctor {:b :test/b
                                                :c :test/c})])

  (a/component :test/b `test-ctor {:c :test/c})

  (a/component :test/c `test-ctor))

(deftest basic-system
  (let [cfg1 (core/build-config '[:org.arachne-framework/arachne-core] `(basic-system-cfg))
        cfg2 (core/build-config '[:org.arachne-framework/arachne-core] `(basic-system-cfg-nested))]

    (doseq [cfg [cfg1 cfg2]]
      (let [rt (component/start (rt/init cfg [:arachne/id :test/rt]))
            a-eid (cfg/q cfg '[:find ?a .
                               :where [?rt :arachne.runtime/components ?a]])]

        (is (rt/lookup rt a-eid))
        (is (rt/lookup rt [:arachne/id :test/b]))
        (is (rt/lookup rt [:arachne/id :test/c]))

        (is (:running? (rt/lookup rt a-eid)))
        (is (:running? (rt/lookup rt [:arachne/id :test/b])))
        (is (:running? (rt/lookup rt [:arachne/id :test/c])))

        (is (= (rt/lookup rt [:arachne/id :test/b])
              (:b (rt/lookup rt a-eid))))

        (is (= (rt/lookup rt [:arachne/id :test/c])
              (:c (rt/lookup rt a-eid))))

        (is (= (rt/lookup rt [:arachne/id :test/c])
              (:c (rt/lookup rt [:arachne/id :test/b]))))

        (component/stop rt)))))

(defn missing-runtime-cfg []

  (a/runtime :test/rt [:test/a])
  (a/component :test/a `test-ctor))

(deftest missing-runtime
  (let [cfg (core/build-config '[:org.arachne-framework/arachne-core]
              `(missing-runtime-cfg))]

    (is (thrown-with-msg? arachne.ArachneException #"no-such-runtime"
          (component/start (rt/init cfg [:arachne/id :test/no-such-runtime]))))))

(defn dependencies-cfg []

  (a/runtime :test/rt [:test/a])
  (a/component :test/a `test-ctor {:b :test/b})
  (a/component :test/b `test-ctor {:c :test/c})
  (a/component :test/c `test-ctor {:d :test/d
                                   :e :test/e})
  (a/component :test/d `test-ctor)
  (a/component :test/e `test-ctor))

(deftest dependencies-test
  (let [cfg (core/build-config '[:org.arachne-framework/arachne-core]
              `(dependencies-cfg))]
    (is (= 4 (count (cfg/dependencies cfg (cfg/attr cfg [:arachne/id :test/a] :db/id)))))
    (is (= 3 (count (cfg/dependencies cfg (cfg/attr cfg [:arachne/id :test/b] :db/id)))))
    (is (= 2 (count (cfg/dependencies cfg (cfg/attr cfg [:arachne/id :test/c] :db/id)))))
    (is (= 0 (count (cfg/dependencies cfg (cfg/attr cfg [:arachne/id :test/d] :db/id)))))))