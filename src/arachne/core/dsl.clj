(ns arachne.core.dsl
  "User-facing DSL use in init scripts"
  (:require [arachne.core.config :as cfg]
            [arachne.core.config.init :as init]))

(defn component
  "Defines a named component by providing an ID, a dependency specification map,
  and a symbol for a component constructor."
  [id dependencies constructor]
  (assert (and (keyword? id) (namespace id))
    "The component ID must be a namespaced keyword")
  (assert (and (map? dependencies)
            (every? (fn [[k v]]
                      (and (keyword k) (keyword v)
                        (namespace k) (not (namespace v))))
              dependencies))
    "The dependency specification must be a map of Arachne IDs (namespaced
    keywords) to non-namespaced keywords.")
  (assert (and (symbol? constructor) (namespace constructor))
    "The constructor should be a namespaced symbol referring to a component
    constructor function.")
  (init/update
    (fn [cfg]
      (cfg/update
        cfg [{:arachne/id id
              :arachne.component/constructor (str constructor)
              :arachne.component/dependencies
              (map (fn [[id key]]
                     {:arachne.component.dependency/entity {:arachne/id id}
                      :arachne.component.dependency/key key})
                dependencies)}]))))

