(ns arachne.core.config.init
  "Initialziation & script support for user configuration values."
  (:refer-clojure :exclude [update])
  (:require [arachne.core.config :as cfg]
            [arachne.core.util :as u]
            [arachne.error :as e :refer [error deferror]]
            [clojure.edn :as edn])
  (:import [java.util UUID]))

(def
  ^{:dynamic true
    :private true
    :doc "An atom containing the configuration currently in context in this init script"}
  *config*)

(deferror ::context-config-outside-of-script
  :message "Cannot reference context config in non-script context"
  :explanation "You attempted to use one of Arachne's script-building DSL forms, but you're not currently in the context of a config initialization script. The script DSL forms work by imperatively updating a configuration that's currently \"in context\"; it is not meaningful to call DSL forms by themselves, or at the REPL."
  :suggestions ["Use this DSL form only inside a config initalization script (such as you would pass to `arachne.core/build-config.)`"])

(defn context-config
  "Return the config value currently in context."
  []
  (if-not (bound? #'*config*)
    (error ::context-config-outside-of-script {})
    @*config*))

(defn update
  "Update the current configuration by applying a function which takes the
  existing config and returns a new one. Additional arguments are passed to the
  supplied function."
  [f & args]
  (if-not (bound? #'*config*)
    (error ::context-config-outside-of-script {})
    (apply swap! *config* f args)))

(defn init-script-ns?
  "Test if a StackTraceElement is from a config init script"
  [^StackTraceElement ste]
  (re-matches #"^arachne_init_script_.*" (.getClassName ste)))

(defn transact
  "Update the current configuration with the given txdata. Does not add provenance info."
  [txdata]
  (update (fn [cfg] (cfg/update cfg txdata))))

(deferror ::nonexistent-aid
  :message "Could not find entity identified by `:aid`"
  :explanation "An entity with an Arachne ID of `:aid` was referenced from a `:dsl-form` DSL form. However, no entity with that Arachne ID actually exists in the config, yet.

  The `:dsl-form` function does require that the entities it references be declared before they are used."
  :suggestions ["Ensure that you have already created entities with the specified Arachne ID in your config script."
                "Make sure that the Arachne IDs match exactly, with no typos."]
  :tx-data-docs {:cfg "The config as of this invocation"
                 :aid "The missing Arachne ID"
                 :dsl-form "The DSL form in question"})

(defn resolve-aid
  "Return the EID of the entity with the specified Arachne ID, in the context config script. If it doesn't exist, throw an error explaining what happened."
  [aid dsl-form]
  (let [cfg (context-config)]
    (if-let [eid (cfg/attr cfg [:arachne/id aid] :db/id)]
      eid
      (error ::nonexistent-aid {:cfg cfg, :aid aid, :dsl-form dsl-form}))))

(defn- add-config-entity
  "Given a freshly initialized configuration, add a reified Configuration
  entity, referencing all the Runtime entities present in the config. Is a no-op
  if a configuration entity already exists in the given config value."
  [config]
  (if (cfg/q config '[:find ?cfg .
                            :where [?cfg :arachne.configuration/roots _]])
    config
    (let [runtimes (cfg/q config '[:find [?rt ...]
                                   :where
                                   [?rt :arachne.runtime/components _]])]
      (cfg/with-provenance :system `add-config-entity
        (cfg/update config [{:arachne.configuration/roots runtimes}])))))

(defn- in-script-ns
  "Invoke the given no-arg function in the context of a new, unique namespace"
  [f]
  (binding [*ns* *ns*]
    (let [script-ns (symbol (str "arachne-init-script-" (UUID/randomUUID)))]
      (in-ns script-ns)
      (clojure.core/with-loading-context (clojure.core/refer 'clojure.core))
      (f))))

(defmacro defdsl
  "Convenience macro to define a DSL function that tracks provenance
   metadata, and validates its arguments according to the registered spec"
  [name docstr argvec & body]
  (let [fqn (symbol (str (ns-name *ns*)) (str name))]
    `(do
       (defn ~name ~docstr [& args#]
         (let [~argvec args#]
           (apply e/assert-args  (quote ~fqn) args#)
           (cfg/with-provenance :user (quote ~fqn)
             :stack-filter-pred init-script-ns?
             ~@body)))
       (alter-meta! (var ~name) assoc :arglists (list (quote ~argvec))))))

(defn apply-initializer
  "Applies the given initializer to the specified config"
  [cfg initializer]
  (binding [*config* (atom cfg)]
    (cond
      (string? initializer) (in-script-ns #(load-file initializer))
      (vector? initializer) (update cfg/update initializer)
      (not-empty initializer) (in-script-ns #(eval initializer))
      :else nil)
    (add-config-entity @*config*)))

(defn initialize
  "Create a brand new configuration using the given modules, initialized with a
  script, form or literal txdata."
  [modules initializer]
  (apply-initializer (cfg/new modules) initializer))

