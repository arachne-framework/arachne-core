(ns arachne.core.module
  "The namespace used for defining and loading Arachne modules"
  (:refer-clojure :exclude [load sort])
  (:require [clojure.edn :as edn]
            [clojure.set :as set]
            [clojure.spec.alpha :as s]
            [loom.graph :as loom]
            [loom.alg :as loom-alg]
            [arachne.aristotle :as ar]
            [arachne.aristotle.registry :as reg]
            [arachne.aristotle.graph :as graph]
            [arachne.log :as log]
            [arachne.core.descriptor :as d]
            [arachne.error :as e :refer [error deferror]]
            [arachne.error.format :as efmt]
            [arachne.core.util :as u]
            [clojure.java.io :as io]))

(reg/prefix :org.arachne-framework "http://arachne-framework.org/name/")
(reg/prefix :arachne.* "urn:arachne:")

(defn- module-definitions
  "Return a set of all `arachne.edn` files on the classpath, as URLs."
  []
  (->> "arachne.edn"
       (.getResources (.getContextClassLoader (Thread/currentThread)))
       enumeration-seq))

(defn- load-definition
  "Given the URL of an module definition EDN file, load the EDN into the
  given descriptor."
  [desc definition]
  (d/with-provenance `load-definition
    (d/add-file! desc definition)))

(defn- module-graph
  [d]
  (let [results (d/query d '[?module ?dep]
                         '[:bgp
                           [?module :arachne.module/dependencies ?dep]])
        results-m (reduce (fn [g [m dep]]
                            (update g m (fnil conj #{}) dep))
                          {}
                          results)]
    (loom/digraph results-m)))

(defn- modules
  "Query a descriptor to return all the modules contained by that descriptor."
  [d module]
  (let [graph (module-graph d)
        ordered (loom-alg/topsort graph)
        module (graph/data (graph/node module))]
    (filter (set (loom-alg/pre-traverse graph module)) ordered)))

(defn- initialize
  "Given a module ID and a descriptor, load all its initializers into the descriptor."
  [d m]
  (let [initializers (d/query d '[?m ?i]
                              '[:bgp [?m :arachne.module/include ?i]]
                              {'?m m})]
    (d/with-provenance `initialize
      (doseq [[m i] initializers]
        (log/info :msg "Initializing module" :module m :initializer i)
        (cond
          (string? i) (d/add-file! d i)
          (and (symbol? i) (namespace i)) (let [v (u/require-and-resolve i)]
                                            (d/update! d (if (fn? v) (v) @v)))
          ;(and (symbol? i) (not (namespace i))) (script/apply-script d i)
          :else (throw (ex-info "Value of :arachne.module/data is inot a valid type for an initializer."
                                {:initializer i}))))))
  d)

(defn- configure
  "Given a module ID and a descriptor, apply all the module's configure functions."
  [d m]
  (let [configurers (d/query d '[?c]
                             '[:bgp [?m :arachne.module/configure ?c]]
                             {'?m m})]
    (d/with-provenance `configure
      (doseq [[c] configurers]
        (log/info :msg "Configuring module" :module m :configure c)
        ((u/require-and-resolve c) d)))))

(defn ^:no-doc descriptor
  "Build a new application descriptor based on the given base module and
  all its dependencies."
  [module data validate?]
  (let [d (d/new)]
    (doseq [def (module-definitions)] (load-definition d def))
    (when-not (empty? data) (d/update! d data))
    (let [modules (modules d module)]
      (doseq [m (reverse modules)] (initialize d m))
      (doseq [m modules] (configure d m)))
    (when validate? (d/validate d))
    d))

(comment

  (require '[clj-memory-meter.core :as mm])

  (def d (descriptor :org.arachne-framework/test-app nil true))
  )

;; TODO: - implement descriptor DSL loading
;; TODO: - audit tests
;; TODO: - get CI working again
;; TODO: - move on to other metrics.
