(ns arachne.core.config.script
  "Initialziation & script support for user configuration values."
  (:refer-clojure :exclude [update])
  (:require [arachne.core.config :as cfg]
            [arachne.core.util :as u]
            [arachne.error :as e :refer [error deferror]]
            [clojure.edn :as edn]
            [clojure.spec :as s]
            [arachne.core.util :as util])
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
  (re-matches #"^arachne_config_script.*" (.getClassName ste)))

(defn transact
  "Update the context configuration with the given txdata. If a tempid is provided as an optional
   second argument, then the resolved entity ID will be returned (otherwise nil.)"
  ([txdata] (transact txdata nil))
  ([txdata tid]
   (let [new-cfg (update cfg/update txdata)]
     (when tid (cfg/resolve-tempid new-cfg tid)))))

(deferror ::nonexistent-aid
  :message "Could not find entity identified by `:aid`"
  :explanation "An entity with an Arachne ID of `:aid` was referenced from a `:dsl-fn` DSL form. However, no entity with that Arachne ID actually exists in the config, yet.

  The `:dsl-fn` function does require that the entities it references be concretely defined in the context configuration before they can be used."
  :suggestions ["Ensure that you have already created entities with the specified Arachne ID in your config script."
                "Make sure that the Arachne IDs match exactly, with no typos."]
  :tx-data-docs {:cfg "The config as of this invocation"
                 :aid "The missing Arachne ID"
                 :dsl-fn "The DSL form in question"})

(defn resolve-aid
  "Return the EID of the entity with the specified Arachne ID, in the context config script. If it
   doesn't exist, throw an error explaining what happened."
  [aid]
  (let [cfg (context-config)
        dsl-fn (or (str cfg/*dsl-function*) "<unknown>")]
    (if-let [eid (cfg/attr cfg [:arachne/id aid] :db/id)]
      eid
      (error ::nonexistent-aid {:cfg cfg, :aid aid, :dsl-fn dsl-fn}))))

(defmacro config
  "Header for a configuration DSL script. Identical in form and function to `clojure.core/ns`,
   except does not allow specification of a namespace name (since config scripts are not part of a
   project's codebase and may not be required."
  [& body]
  `(ns ~(gensym "arachne-config-script") ~@body))

(defn- in-script-ns
  "Invoke the given no-arg function in the context of a new, unique namespace"
  [f]
  (binding [*ns* *ns*]
    (let [script-ns (gensym "arachne-config-script")]
      (in-ns script-ns)
      (clojure.core/with-loading-context (clojure.core/refer 'clojure.core))
      (refer 'arachne.core.config.script :only ['config])
      (f))))

(defmacro defdsl
  "Convenience macro to define a DSL function that tracks provenance
   metadata, and validates its arguments according to the provided argument specs.

   Also, unhygenically exposes a `&args` symbol to the body. This is bound to the conformed value
   of the arguments and is useful for reducing spec boilerplate."
  [name docstr argspec argvec & body]
  (let [fqn (symbol (str (ns-name *ns*)) (str name))]
    `(do
       (s/fdef ~name :args ~argspec)
       (defn ~name ~docstr [& args#]
         (let [~argvec args#]
           (apply e/assert-args (quote ~fqn) args#)
           (cfg/with-provenance :user (quote ~fqn)
             :stack-filter-pred init-script-ns?
             (let [~'&args (s/conform (:args (s/get-spec (quote ~fqn))) args#)]
               ~@body))))
       (alter-meta! (var ~name) assoc :arglists (list (quote ~argvec)))
       (var ~name))))

(defn apply-initializer
  "Applies the given initializer to the specified config"
  [cfg initializer]
  (binding [*config* (atom cfg)]
    (cond
      (symbol? initializer) (swap! *config* (util/require-and-resolve initializer))
      (string? initializer) (in-script-ns #(load-file initializer))
      (vector? initializer) (swap! *config* cfg/update initializer)
      (not-empty initializer) (in-script-ns #(eval initializer))
      :else nil)
    @*config*))