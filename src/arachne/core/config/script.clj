(ns arachne.core.config.script
  "Initialziation & script support for user configuration values."
  (:refer-clojure :exclude [update])
  (:require [arachne.core.config :as cfg]
            [arachne.core.config.impl.debug :as debug-cfg]
            [arachne.core.util :as u]
            [arachne.error :as e :refer [error deferror]]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.spec.alpha :as s]
            [clojure.tools.namespace.find :as find]
            [clojure.tools.namespace.reload :as reload]
            [clojure.java.classpath :as cp])
  (:import [java.util UUID]))

(def
  ^{:dynamic true
    :doc "An atom containing the configuration currently in context in this init script"}
  *config*)

(def
  ^{:dynamic true
    :doc "Modifies behavior if a DSL form is evaluated outside of a config script context.
          If true, will dump txdata to stdout instead of failing."}
  *debug-dsl-txdata* false)

(deferror ::no-context-cfg
  :message "Cannot use config DSL outside of config script context."
  :explanation "You attempted to use one of Arachne's script-building DSL forms, but you're not currently in the context of a config initialization script. Without a \"current\" config to update, the DSL forms can't do anything meaningful.

  If you just want to debug the DSL form and see what kind of changes it would transact to a config (if there was a real one in context), you can bind *debug-dsl-txdata* to true by calling `arachne.core.dsl/enable-debug!`

  When debugging is enabled, evaluating a DSL form outside a script context will not throw an error, but it won't update a config either. Instead, it will print the transaction data that a DSL form to System/out. This cannot be used to run Arachne, but it is useful for debugging DSL forms."
  :suggestions ["Use this DSL form only inside a config initalization script (such as you would pass to `arachne.core/build-config.)`"])

(defn context-config
  "Return the config value currently in context."
  []
  (if-not (bound? #'*config*)
    (if *debug-dsl-txdata*
      (debug-cfg/->DebugConfig)
      (error ::no-context-cfg {}))
    @*config*))

(defn update
  "Update the current configuration by applying a function which takes the
  existing config and returns a new one. Additional arguments are passed to the
  supplied function."
  [f & args]
  (if-not (bound? #'*config*)
    (if *debug-dsl-txdata*
      (apply f (debug-cfg/->DebugConfig) args)
      (error ::no-context-cfg {}))
    (apply swap! *config* f args)))

(defn ^:no-doc in-script-ns?
  "Return a predicate function to test if a StackTraceElement is from the specified script"
  [ns]
  (let [nsname (ns-name ns)
        classname (str/replace nsname \- \_)]
    (fn [ste]
      (.startsWith (.getClassName ste) classname))))

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
  :ex-data-docs {:cfg "The config as of this invocation"
                 :aid "The missing Arachne ID"
                 :dsl-fn "The DSL form in question"})

(defmacro ^:private in-script-ns
  "Invoke the body in the context of a new, unique config namespace"
  [& body]
  (let [script-ns (gensym "arachne-config-script")]
    `(binding [*ns* *ns*]
       (ns ^:config ~script-ns)
       ~@body)))

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
             :stack-filter-pred (in-script-ns? *ns*)
             (let [~'&args (s/conform (:args (s/get-spec (quote ~fqn))) args#)]
               ~@body))))
       (alter-meta! (var ~name) assoc :arglists (list (quote ~argvec)))
       (var ~name))))


(deferror ::config-ns-not-found
  :message "Could not find config namespace `:ns`"
  :explanation "You specified that `:ns` was an Arachne configuration namespace (that is, a namespace containing Arachne configuration DSL forms.)

  However, `:ns` could not be found on the classpath."
  :suggestions ["Ensure that a namespace named `:ns` exists on the classpath."
                "Ensure that the declaration and the useages of `:ns` are all typo-free."]
  :ex-data-docs {:ns "The missing namespace"})

(deferror ::ns-is-not-config-ns
  :message "`:ns` is not a config namespace"
  :explanation "You specified that `:ns` was an Arachne configuration namespace (that is, a namespace containing Arachne configuration DSL forms.)

  Config namespaces are identified by metadata on the namespace itself: specifically, they are expected to have `{:config true}` in the namespace metadata.

  However, `:ns` does not have this metadata."
  :suggestions ["Ensure the `:ns` namespace has a `:config` metadata tag, if it is intended to be a config namespace."
                "Use a different namespace that is actually a config namespace."]
  :ex-data-docs {:ns "The namespace"})

(defn- validate-config-ns
  "Ensure that the given ns is present and a config namespace, throwing an error otherwise."
  [all-nses ns-sym]
  (if-let [found (first (filter #(= ns-sym %) all-nses))]
    (if (:config (meta found))
      ns-sym
      (error ::ns-is-not-config-ns {:ns ns-sym}))
    (error ::config-ns-not-found {:ns ns-sym})))

(defn- unload-config-nses
  "Unload all config namespaces, so that they will be fully re-loaded"
  [all-nses]
  (doseq [lib (filter (comp :config meta) all-nses)]
    (reload/remove-lib lib)))

(defn- load-config-ns
  "Evaluate the config DSL forms defined in the given namespace.

  The specified namespace must should be a config namespace.

  Any config namespaces that the given namespace requires (transitively or directly) will also be reloaded"
  [ns-sym]
  (let [all-nses (find/find-namespaces (cp/classpath))]
    (validate-config-ns all-nses ns-sym)
    (unload-config-nses all-nses)
    (require ns-sym)))

(defn apply-initializer
  "Applies the given initializer to the specified config"
  [cfg initializer]
  (binding [*config* (atom cfg)]
    (cond
      (qualified-symbol? initializer) (swap! *config* (u/require-and-resolve initializer))
      (simple-symbol? initializer) (load-config-ns initializer)
      (string? initializer) (in-script-ns (load-file initializer))
      (vector? initializer) (swap! *config* cfg/update initializer)
      (not-empty initializer) (in-script-ns (eval initializer))
      :else nil)
    @*config*))
