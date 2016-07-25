(ns arachne.core
  "The core Arachne module that bootstraps everything else"
  (:require [arachne.core.module :as m]
            [arachne.core.config :as cfg]
            [arachne.core.config.init :as init]
            [arachne.core.util :as util]
            [arachne.core.schema :as schema]
            [arachne.core.specs]))

(defn schema
  "Return the schema for the core module"
  []
  schema/schema)

(defn configure
  "Configure the core module"
  [cfg]
  cfg)

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
        cfg (reduce (fn [c m] (m/configure m c)) cfg modules)]
    cfg))
