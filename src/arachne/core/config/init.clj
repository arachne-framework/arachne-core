(ns arachne.core.config.init
  "Initialziation & script support for user configuration values."
  (:refer-clojure :exclude [update])
  (:require [arachne.core.config :as cfg]
            [arachne.core.util :as u]
            [clojure.edn :as edn])
  (:import [java.util UUID]))

(def
  ^{:dynamic true
    :doc "An atom containing the configuration currently in context in this init script"}
  *config*)

(u/deferror ::update-outside-script
  "Attemped to update Arachne config outside of a configuration script. You should only use the init-script configuration API during the configuration phase.")

(defn update
  "Update the current configuration by applying a function which takes the
  existing config and returns a new one. Additional arguments are passed to the
  supplied function."
  [f & args]
  (if-not (bound? #'*config*)
    (u/error ::update-outside-script {})
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

(defn- in-script-ns
  "Invoke the given no-arg function in the context of a new, unique namespace"
  [f]
  (binding [*ns* *ns*]
    (let [script-ns (symbol (str "init-script-" (UUID/randomUUID)))]
      (in-ns script-ns)
      (clojure.core/with-loading-context (clojure.core/refer 'clojure.core))
      (f))))

(defn initialize
  "Create a brand new configuration with the given namespace, using the given
  modules, initialized with a script, form or literal txdata."
  [config-ns modules initializer]
  (binding [*config* (atom (cfg/new modules))]
    (cond
      (string? initializer) (in-script-ns #(load-file initializer))
      (list? initializer) (in-script-ns #(eval initializer))
      (not-empty initializer) (update cfg/update initializer)
      :else nil)
    (add-config-entity @*config* config-ns)))