(ns arachne.core
  "The core Arachne module that bootstraps everything else"
  (:require [arachne.core.module :as m]
            [arachne.core.config :as cfg]
            [arachne.core.config.init :as init]
            [arachne.core.util :as util]))

(defn schema
  "Return the schema for the core module"
  []
  (util/read-edn "arachne/core/schema.edn"))

(defn configure
  "Configure the core module"
  [cfg]
  cfg)

(defn build-config
  "TODO: spec & document"
  [root-modules & initializers]
  (let [modules (m/load root-modules)
        cfg (cfg/new modules)
        cfg (reduce (fn [c i] (init/initialize c i)) cfg initializers)
        cfg (reduce (fn [c m] (m/configure m c)) cfg modules)]
    cfg))
