(ns arachne.core
  "The core Arachne module that bootstraps everything else"
  (:require [arachne.core.module :as m]
            [arachne.core.config :as cfg]
            [arachne.core.config.init :as init]
            [arachne.core.util :as util]
            [arachne.core.schema :as schema]))

(defn schema
  "Return the schema for the core module"
  []
  schema/schema)

(defn configure
  "Configure the core module"
  [cfg]
  cfg)

(defn build-config
  "TODO: spec & document"
  [config-ns root-modules initializer]
  (let [modules (m/load root-modules)
        cfg (init/initialize config-ns modules initializer)
        cfg (reduce (fn [c m] (m/configure m c)) cfg modules)]
    cfg))
