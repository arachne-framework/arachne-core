(ns arachne.core.config.init
  "Initialziation & script support for user configuration values."
  (:refer-clojure :exclude [update])
  (:require [arachne.core.config :as cfg]
            [clojure.edn :as edn]))

(def ^:private ^:dynamic *config*)

(defn update
  "Update the current configuration by applying a function which takes the
  existing config and returns a new one. Additional arguments are passed to the
  supplied function."
  [f & args]
  (if-not (bound? #'*config*)
    (throw (ex-info "Attemped to update Arachne config outside of a configuration script. You should only use the init-script configuration API during the configuration phase." {}))
    (apply swap! *config* f args)))

(defn transact
  "Update the current configuration with the given txdata."
  [txdata]
  (update (fn [cfg] (cfg/update cfg txdata))))

(defn- add-config-entity
  "Given a freshly initialized configuration, add a reified Configuration
  entity, referencing all the Runtime entities present in the config."
  [config config-ns]
  (let [runtimes (cfg/q config '[:find [?rt ...]
                                 :where
                                 [?rt :arachne.runtime/components _]])]
    (cfg/update config [{:arachne.configuration/namespace config-ns
                         :arachne.configuration/roots runtimes}])))

(defn initialize
  "Create a brand new configuration with the given namespace, using the given
  modules, initialized with a script, form or literal txdata."
  [config-ns modules initializer]
  (binding [*config* (atom (cfg/new modules))]
    (cond
      (string? initializer) (load-file initializer)
      (list? initializer) (eval initializer)
      (not-empty initializer) (update cfg/update initializer)
      :else nil)
    (add-config-entity @*config* config-ns)))