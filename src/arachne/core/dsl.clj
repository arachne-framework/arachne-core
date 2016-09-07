(ns arachne.core.dsl
  "User-facing DSL use in init scripts"
  (:require [arachne.core.config :as cfg]
            [arachne.core.config.init :as init :refer [defdsl]]
            [arachne.core.dsl.specs]
            [arachne.core.util :as util]
            [clojure.spec :as s]))

(defdsl runtime
  "Defines a named Arachne runtime containing the given root components, which
  may be specified either as entity IDs or Arachne IDs. Returns the entity ID of
  the runtime."
  [id roots]
  (let [txdata [{:arachne/id id
                 :arachne.runtime/components (map (fn [root]
                                                    {(if (keyword? root)
                                                       :arachne/id
                                                       :db/id) root})
                                               roots)}]]
    (cfg/attr (init/transact txdata) [:arachne/id id] :db/id)))

(defdsl component
  "Defines a component by providing an optional Arachne ID, an optional
  dependency specification map, and a symbol for a component constructor.
  Returns the entity ID of the newly-constructed component.

  The dependency map is a mapping from Arachne IDs or entity IDs identifying the
  component to the keyword that will be used to assoc the component instance at
  runtime."
  [& args]
  (let [parsed (s/conform (:args (s/get-spec `component)) args)
        dependencies (:dependencies parsed)
        arachne-id (:arachne-id parsed)
        constructor (:constructor parsed)
        tid (cfg/tempid)
        deps (map (fn [[id key]]
                    {:arachne.component.dependency/entity {(if (keyword? id)
                                                             :arachne/id
                                                             :db/id) id}
                     :arachne.component.dependency/key key})
               dependencies)
        component (util/mkeep
                    {:db/id tid
                     :arachne/id arachne-id
                     :arachne.component/constructor (keyword constructor)
                     :arachne.component/dependencies deps})]
    (cfg/resolve-tempid (init/transact [component]) tid)))