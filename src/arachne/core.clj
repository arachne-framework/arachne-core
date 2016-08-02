(ns arachne.core
  "The core Arachne module that bootstraps everything else"
  (:require [arachne.core.module :as m]
            [arachne.core.config :as cfg]
            [arachne.core.config.init :as init]
            [arachne.core.util :as util]
            [arachne.core.schema :as schema]
            [arachne.core.specs]))

(defn instance-ctor
  "Component constructor that defines components by resolving a var"
  [component]
  @(util/require-and-resolve (:arachne.component/instance component)))

(defn- add-instance-constructors
  "Implement :arachne.component/instance in terms of :arachne.component/constructor"
  [cfg]
  (let [components (cfg/q cfg '[:find [?c ...]
                                :where
                                [?c :arachne.component/instance ?var]])]
    (if (empty? components)
      cfg
      (cfg/update cfg
        (for [c components]
          {:db/id c
           :arachne.component/constructor :arachne.core/instance-ctor})))))

(defn schema
  "Return the schema for the core module"
  []
  schema/schema)

(defn configure
  "Configure the core module"
  [cfg]
  (add-instance-constructors cfg))

(defn build-config
  "Construct a configuration, using the specified namespace. The specified modules will participate in the configuration after initializing it with the given user-supplied initializer value.

  The initializer can be one of several types:

  - A string, as the path to a config initialization script
  - A list, as a form that will be evaluated as an initialization script
  - A vector, as raw configuration txdata"
  [config-ns root-modules initializer]
  (util/validate-args `build-config config-ns root-modules initializer)
  (let [modules (m/load root-modules)
        cfg (init/initialize config-ns modules initializer)
        cfg (reduce (fn [c m] (m/configure m c)) cfg (reverse modules))]
    cfg))
