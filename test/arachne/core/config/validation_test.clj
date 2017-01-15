(ns arachne.core.config.validation-test
  (:require [clojure.test :refer :all]
            [arachne.core :as core]
            [arachne.core.runtime :as rt]
            [arachne.core.config :as cfg]
            [arachne.error :as e]
            [com.stuartsierra.component :as component]
            [arachne.core.dsl :as dsl]))

(defn validator-1
  "Test validator, always throws"
  [cfg]
  [(ex-info "validator-1" {:data 42
                           :more-data "hi!"})])

(defn validator-2
  "Test validator, always throws"
  [cfg]
  [(ex-info "validator-2" {:data true})
   (ex-info "validator-2" {:data false})])

(deftest basic-validations
  (let [cfg-txdata [{:arachne.configuration/namespace :test
                     :arachne.configuration/validators [:arachne.core.config.validation-test/validator-1
                                                        :arachne.core.config.validation-test/validator-2]
                     :arachne.configuration/roots {:arachne/id :test/rt}}]]
    (is (thrown-with-msg? arachne.ArachneException #"3 errors"
          (cfg/with-provenance :test `basic-validations
            (core/build-config '[:org.arachne-framework/arachne-core] cfg-txdata))))))

(defn min-cardinality-test-cfg []
  (dsl/runtime :test/rt [:test/a])
  (dsl/transact [{:arachne/id :test/a
                  :arachne.component/dependencies
                  {:arachne.component.dependency/entity {:arachne/id :test/b}
                   :arachne.component.dependency/key :key}}])

  (dsl/component :test/b 'test/ctor))

(deftest min-cardinality-test
  (is (thrown-with-msg? arachne.ArachneException #"1 errors"
        (core/build-config '[:org.arachne-framework/arachne-core] `(min-cardinality-test-cfg)))))

(defn max-cardinality-test-cfg []
  (dsl/transact [{:db/ident :arachne.runtime/components
                  :arachne.attribute/max-cardinality 2}])

  (dsl/runtime :test/rt [:test/a :test/b :test/c])
  (dsl/component :test/a 'test/ctor)
  (dsl/component :test/b 'test/ctor)
  (dsl/component :test/c 'test/ctor))


(deftest max-cardinality-test
  (is (thrown-with-msg? arachne.ArachneException #"1 errors"
        (core/build-config '[:org.arachne-framework/arachne-core] `(max-cardinality-test-cfg)))))

(defn ref-classes-test-cfg []
  (dsl/runtime :test/rt [:test/a])

  (dsl/transact [{:arachne/id :test/a}]))

(deftest ref-classes-test
  (is (thrown-with-msg? arachne.ArachneException #"1 errors"
        (core/build-config '[:org.arachne-framework/arachne-core] `(ref-classes-test-cfg)))))


(defn instance-of-test-cfg []
  (dsl/runtime :test/rt [:test/a])
  (dsl/component :test/a 'clojure.core/hash-map)

  (dsl/component :test/b 'clojure.core/hash-map)

  ;; By stating that B is a runtime, we provoke a min-cardinality error...
  (dsl/transact [{:arachne/id :test/b
                  :arachne/instance-of [:db/ident :arachne/Runtime]}]))

(deftest instance-of-test
  (is (thrown-with-msg? arachne.ArachneException #"1 errors"
        (core/build-config '[:org.arachne-framework/arachne-core] `(instance-of-test-cfg)))))