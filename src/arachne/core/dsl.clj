(ns arachne.core.dsl
  "User-facing DSL use in init scripts"
  (:require [arachne.core.config :as cfg]
            [arachne.core.config.init :as init]
            [arachne.core.dsl.specs]
            [arachne.core.util :as util]))

(defn runtime
  "Defines a named Arachne runtime containing the given root components."
  [id roots]
  (util/validate-args `runtime id roots)
  (init/update
    (fn [cfg]
      (cfg/update cfg
        [{:arachne/id id
          :arachne.runtime/components (map (fn [root-id]
                                             {:arachne/id root-id})
                                        roots)}]))))

(defn component
  "Defines a named component by providing an ID, a dependency specification map,
  and a symbol for a component constructor."
  [id dependencies constructor]
  (util/validate-args `component id dependencies constructor)
  (init/update
    (fn [cfg]
      (let [deps (map (fn [[id key]]
                        {:arachne.component.dependency/entity {:arachne/id id}
                         :arachne.component.dependency/key    key})
                      dependencies)
            component {:arachne/id id
                       :arachne.component/constructor (keyword constructor)}
            component (if (empty? deps)
                        component
                        (assoc component :arachne.component/dependencies deps))]
        (cfg/update
          cfg [component])))))
