(ns arachne.core.runtime.validation-test
  (:require [clojure.test :refer :all]
            [arachne.core :as core]
            [arachne.core.config :as cfg]
            [arachne.core.config.model :as m]
            [arachne.core.runtime :as rt]
            [com.stuartsierra.component :as component]
            [arachne.core.util :as util]
            [clojure.spec :as s]
            [clojure.tools.logging :as log]
            [arachne.core.config.script :as init]
            [arachne.core.config.impl.multiplex :as impl]))



(defprotocol Widget
  (dance [this] "Do the widget dance"))

(defrecord TestWidget []
  Widget
  (dance [_] (log/debug "the widget dances")))


(defrecord NotAWidget [])

(s/def ::widget #(satisfies? Widget %))

(def module-schema
  (concat

    (m/type :test/Widget [:arachne/Component]
      "Type of Widget components"
      ::widget
      (m/attr :test.widget/name :one :string
        "The name of a widget"))

    ))

(defn- setup
  []
  (cfg/init (impl/new) [(arachne.core/schema) module-schema]))

(deftest successful-validation
  (let [cfg (setup)
        cfg (init/apply-initializer cfg
              '(do
                 (require '[arachne.core.dsl :as c])

                 (c/runtime :test/rt [:test/a])
                 (c/transact [{:arachne/id :test/a
                               :test.widget/name "foo"
                               :arachne.component/constructor :arachne.core.runtime.validation-test/->TestWidget}])))

        rt (component/start (rt/init cfg [:arachne/id :test/rt]))]
    (is (instance? arachne.core.runtime.ArachneRuntime rt))))

(deftest failing-validation
  (let [cfg (setup)
        cfg (init/apply-initializer cfg
              '(do
                 (require '[arachne.core.dsl :as c])

                 (c/runtime :test/rt [:test/a])

                 (c/transact [{:arachne/id :test/a
                               :test.widget/name "foo"
                               :arachne.component/constructor :arachne.core.runtime.validation-test/->NotAWidget}])))]
    (is (thrown-with-msg? Throwable #"Error in component"
          (component/start (rt/init cfg [:arachne/id :test/rt]))))))