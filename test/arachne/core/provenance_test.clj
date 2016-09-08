(ns arachne.core.provenance-test
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

(deftest with-provenance-test
  (let [cfg (core/build-config '[:org.arachne-framework/arachne-core]
              "test/arachne/core/provenance_test/script.clj")]
    (is (= 1 (count
               (cfg/q cfg '[:find ?tx
                            :in $
                            :where
                            [?rt :arachne.runtime/components _ ?tx]
                            [?tx :arachne.transaction/source :user]
                            [?tx :arachne.transaction/source-file "script.clj"]
                            [?tx :arachne.transaction/source-line 3]
                            [?tx :arachne.transaction/function :arachne.core.dsl/runtime]]))))))

