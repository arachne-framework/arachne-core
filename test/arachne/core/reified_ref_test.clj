(ns arachne.core.reified-ref-test
  (:require [clojure.test :refer :all]
            [arachne.core :as core]
            [arachne.core.runtime :as rt]
            [arachne.core.config :as cfg]
            [com.stuartsierra.component :as component]
            [arachne.core.dsl :as a]))

(arachne.error/explain-test-errors!)

(defn rref-target-cfg []

  (a/id :test/rt (a/runtime [:test/a]))
  (a/id :test/a (a/component `test-ctor)))


(deftest rref-target
  (let [cfg (core/build-config '[:org.arachne-framework/arachne-core] `(rref-target-cfg))]
    (is (cfg/q cfg '[:find ?rt .
                     :where
                     [?rt :arachne/id :test/rt]
                     [?rt :arachne.runtime/components ?c]
                     [?c :arachne/id :test/a]]))
    (is (empty? (cfg/q cfg '[:find ?rr
                             :where
                             [?rr :arachne.reified-reference/attr _]])))))


(defn rref-src-cfg []

  (a/transact [{:arachne.reified-reference/attr :arachne/id
                :arachne.reified-reference/value :test/rt
                :arachne.runtime/components [{:arachne/id :test/a
                                              :arachne.component/constructor :test/ctor}]}])

  (a/transact [{:arachne/id :test/rt
                :arachne.runtime/components [{:arachne/id :test/b
                                              :arachne.component/constructor :test/ctor}]}])

    )

(deftest rref-src
  (let [cfg (core/build-config '[:org.arachne-framework/arachne-core] `(rref-src-cfg))]
    (is (cfg/q cfg '[:find ?rt .
                     :where
                     [?rt :arachne/id :test/rt]
                     [?rt :arachne.runtime/components ?ca]
                     [?rt :arachne.runtime/components ?cb]
                     [?ca :arachne/id :test/a]
                     [?cb :arachne/id :test/b]]))
    (is (empty? (cfg/q cfg '[:find ?rr
                             :where
                             [?rr :arachne.reified-reference/attr _]])))))

