(ns arachne.core.runtime.validation-test
  (:require [clojure.test :refer :all]
            [arachne.core :as core]
            [arachne.core.config :as cfg]
            [arachne.core.config.ontology :as o]
            [arachne.core.runtime :as rt]
            [com.stuartsierra.component :as component]
            [arachne.core.util :as util]
            [clojure.spec :as s]
            [clojure.tools.logging :as log]
            [arachne.core.config.init :as init]))

(def core
  {:db/id (cfg/tempid)
   :arachne.module/name 'org.arachne-framework/arachne-core
   :arachne.module/configure 'arachne.core/configure
   :arachne.module/schema 'arachne.core/schema})

(def module
  {:db/id (cfg/tempid)
   :arachne.module/name `module
   :arachne.module/configure `module
   :arachne.module/schema `module-schema})

(defprotocol Widget
  (dance [this] "Do the widget dance"))

(defrecord TestWidget []
  Widget
  (dance [_] (log/debug "the widget dances")))


(defrecord NotAWidget [])

(s/def ::widget #(satisfies? Widget %))

(def module-schema
  (constantly (concat

                (o/class :test/Widget [:arachne/Component]
                  "Class of Widget components"
                  ::widget
                  (o/attr :test.widget/name :one :string
                    "The name of a widget"))

                )))

(defn- setup
  []
  (cfg/new [core module]))

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