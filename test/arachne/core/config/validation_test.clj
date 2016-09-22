(ns arachne.core.config.validation-test
  (:require [clojure.test :refer :all]
            [arachne.core :as core]
            [arachne.core.runtime :as rt]
            [arachne.core.config :as cfg]
            [com.stuartsierra.component :as component]))

(defn validator-1
  "Test validator, always throws"
  [cfg]
  [{:arachne.core.config.validation/message "validator-1"
    :data 42
    :more-data "hi!"}])

(defn validator-2
  "Test validator, always throws"
  [cfg]
  [{:arachne.core.config.validation/message "validator-2"
    :data true}
   {:arachne.core.config.validation/message "validator-2"
    :data false}])

(deftest basic-validations
  (let [cfg-txdata [{:arachne.configuration/namespace :test
                     :arachne.configuration/validators [:arachne.core.config.validation-test/validator-1
                                                        :arachne.core.config.validation-test/validator-2]
                     :arachne.configuration/roots {:arachne/id :test/rt}}]]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"3 errors"
          (cfg/with-provenance :test `basic-validations
            (core/build-config '[:org.arachne-framework/arachne-core] cfg-txdata))))))

(deftest min-cardinality-test
  (let [script '(do
                  (require '[arachne.core.dsl :as dsl])

                  (dsl/transact [{:arachne/id :test/rt2
                                  :arachne/instance-of {:db/ident :arachne/Runtime}}])

                  (dsl/runtime :test/rt [:test/a])
                  (dsl/component :test/a {} 'test/ctor)

                  )]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"1 errors"
          (core/build-config '[:org.arachne-framework/arachne-core] script)))))

(deftest max-cardinality-test
  (let [script '(do
                  (require '[arachne.core.dsl :as dsl])

                  (dsl/transact [{:db/ident :arachne.runtime/components
                                  :arachne.attribute/max-cardinality 2}])

                  (dsl/runtime :test/rt [:test/a :test/b :test/c])
                  (dsl/component :test/a {} 'test/ctor)
                  (dsl/component :test/b {} 'test/ctor)
                  (dsl/component :test/c {} 'test/ctor)

                  )]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"1 errors"
          (core/build-config '[:org.arachne-framework/arachne-core] script)))))


(deftest ref-classes-test
  (let [script '(do
                  (require '[arachne.core.dsl :as dsl])

                  (dsl/runtime :test/rt [:test/a])

                  (dsl/transact [{:arachne/id :test/a}]))]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"1 errors"
          (core/build-config '[:org.arachne-framework/arachne-core] script)))))
