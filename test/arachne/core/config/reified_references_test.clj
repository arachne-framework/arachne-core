(ns arachne.core.config.reified-references-test
  (:require [clojure.test :refer :all]
            [clojure.set :as set]
            [arachne.core :as core]
            [arachne.core.module :as m]
            [arachne.core.config :as cfg]
            [arachne.core.config.impl.multiplex :as impl]
            [arachne.core.dsl :as a]))

(defn forward-reference-cfg []
  (a/id :test/rt (a/runtime [:test/a :test/b]))
  (a/id :test/a (a/component `test-ctor))
  (a/id :test/b (a/component `test-ctor)))

(deftest forward-references
  (let [cfg (core/build-config '[:org.arachne-framework/arachne-core]
              `(forward-reference-cfg))]
    (is (cfg/q cfg '[:find ?rt .
                     :where
                     [?rt :arachne/id :test/rt]
                     [?rt :arachne.runtime/components ?c1]
                     [?c1 :arachne/id :test/a]
                     [?rt :arachne.runtime/components ?c2]
                     [?c2 :arachne/id :test/b]]))))

(defn typo-cfg [bad-name]
  (a/id :test/rt (a/runtime [:test/some-component]))
  (a/id :test/some-component (a/component `test-ctor {:c bad-name}))
  (a/id :test/some-other-component (a/component `test-ctor)))

(deftest typo-reference
  (is (thrown-with-msg? Throwable #"not found in the config"
        (core/build-config '[:org.arachne-framework/arachne-core]
          `(typo-cfg :not/found))))

  (is (thrown-with-msg? Throwable #"Did you mean"
        (core/build-config '[:org.arachne-framework/arachne-core]
          `(typo-cfg :test/some-ohter-component)))))
