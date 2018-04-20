(ns arachne.core.descriptor.script
  "Descriptor DSL support"
  (:refer-clojure :exclude [update])
  (:require [arachne.core.descriptor :as d]
            [arachne.core.util :as u]
            [arachne.error :as e :refer [error deferror]]
            [arachne.log :as log]
            [arachne.aristotle :as ar]
            [arachne.aristotle.graph :as a.g]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.spec.alpha :as s]
            [clojure.tools.namespace.find :as find]
            [clojure.tools.namespace.reload :as reload]
            [clojure.java.classpath :as cp]
            [clojure.pprint :as pprint])
  (:import [java.util UUID]))

(def debug-descriptor
  (reify d/Synced
    (update- [this f]
      (println "Evaluated Arachne DSL with no descriptor in context. Data will not be applied to any descriptor. The data that would have been added (excluding provenance metadata) was:\n")
      (let [g (ar/graph :simple)]
        (f g)
        (pprint/pprint (a.g/graph->clj g #(not (or (= :rdf/Statement (:rdf/type %))
                                                   (:arachne.stack-frame/class %)
                                                   (:arachne.descriptor/tx %)
                                                   (:arachne.descriptor.tx/index %)
                                                   (:arachne.provenance/stack-frame %)
                                                   (:arachne.provenance/function %)))))))))

(def
  ^{:dynamic true
    :doc "An atom containing the descriptor currently in context in this init script"}
  *descriptor*
  debug-descriptor)

(defn ^:no-doc in-script-ns?
  "Return a predicate function to test if a StackTraceElement is from
  the specified script"
  [ns]
  (let [nsname (ns-name ns)
        classname (str/replace nsname \- \_)]
    (fn [ste]
      (.startsWith (.getClassName ste) classname))))

(defn transact
  "Update the context descriptor to contain the given data. Data can be
  any RDF data that satisfies arachne.aristotle.graph/AsTriples"
  [data]
  (d/update! *descriptor* data))

(defmacro defdsl
  "Convenience macro to define a DSL function that tracks provenance
  metadata, and validates its arguments according to the provided
  argument specs.

   Also, unhygenically exposes a `&args` symbol to the body. This is
  bound to the conformed value of the arguments and is useful for
  reducing spec boilerplate."
  [name docstr argspec argvec & body]
  (let [fqn (symbol (str (ns-name *ns*)) (str name))]
    `(do
       (s/fdef ~name :args ~argspec)
       (defn ~name ~docstr [& args#]
         (let [~argvec args#]
           (apply e/assert-args (quote ~fqn) args#)
           (d/with-provenance
             (quote ~fqn)
             :stack-filter-pred (in-script-ns? *ns*)
             (let [~'&args (s/conform (:args (s/get-spec (quote ~fqn))) args#)]
               ~@body))))
       (alter-meta! (var ~name) assoc :arglists (list (quote ~argvec)))
       (var ~name))))

(deferror ::script-ns-not-found
  :message "Could not find script namespace `:ns`"
  :explanation "You specified that `:ns` was an Arachne descriptor script namespace (that is, a namespace containing Arachne descriptor DSL forms.)

  However, `:ns` could not be found on the classpath."
  :suggestions ["Ensure that a namespace named `:ns` exists on the classpath."
                "Ensure that the declaration and the useages of `:ns` are all typo-free."]
  :ex-data-docs {:ns "The missing namespace"})

(deferror ::ns-is-not-script-ns
  :message "`:ns` is not a descriptor script namespace"
  :explanation "You specified that `:ns` was an Arachne descriptor script namespace (that is, a namespace containing Arachne descriptor DSL forms.)

  Descriptor namespaces are identified by metadata on the namespace itself: specifically, they are expected to have `{:descriptor true}` in the namespace metadata.

  However, `:ns` does not have this metadata."
  :suggestions ["Ensure the `:ns` namespace has a `:descriptor` metadata tag, if it is intended to be a config namespace."
                "Use a different namespace that is actually a descriptor namespace."]
  :ex-data-docs {:ns "The namespace"})

(defn- validate-ns
  "Ensure that the given ns is present and a descriptor namespace,
  throwing an error otherwise."
  [all-nses ns-sym]
  (if-let [found (first (filter #(= ns-sym %) all-nses))]
    (if (:descriptor (meta found))
      ns-sym
      (error ::ns-is-not-script-ns {:ns ns-sym}))
    (error ::script-ns-not-found {:ns ns-sym})))

(defn- unload-script-nses
  "Unload all script namespaces, so that they will be fully re-loaded"
  [all-nses]
  (doseq [lib (filter (comp :descriptor meta) all-nses)]
    (reload/remove-lib lib)))

(defn- load-script-ns
  "Evaluate the descriptor DSL forms defined in the given namespace.

  The specified namespace must should be a script namespace.

  Any config namespaces that the given namespace requires (transitively or directly) will also be reloaded"
  [ns-sym]
  (let [all-nses (find/find-namespaces (cp/classpath))]
    (validate-ns all-nses ns-sym)
    (unload-script-nses all-nses)
    (require ns-sym)))

(defn apply-script
  "Applies the given descriptor DSL script"
  [d ns]
  (binding [*descriptor* d] (load-script-ns ns)))
