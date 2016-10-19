(ns arachne.core.module
  "The namespace used for defining and loading Arachne modules"
  (:refer-clojure :exclude [load sort])
  (:require [clojure.edn :as edn]
            [clojure.set :as set]
            [clojure.spec :as spec]
            [loom.graph :as loom]
            [loom.alg :as loom-alg]
            [arachne.error :as e :refer [error deferror]]
            [arachne.core.util :as u]
            [arachne.core.module.specs]))

(deferror ::missing-module
  :message "Modules `:missing` not found"
  :explanation "Module `:module-name` declared a dependency on modules `:missing`. However, these modules could not be found on the classpath. Modules are defined in `arachne-modules.edn` files that must be on the classpath in order for the module to be discovered."
  :suggestions ["Makes sure you have configured your project's dependencies correctly in your build.boot or project.clj."
                "Make sure the module names are correct and typo-free"]
  :ex-data-docs {:module-name "Name of the module with a missing dep"
                 :module "Module definition with the missing dep "
                 :missing "Name of missing module"})

(deferror ::dup-definition
  :message "Duplicate module definition for `:dup`"
  :explanation "Found two definitions for module named :dup, and the definitions were not the same. This can happen when two different versions of the module are somehow both on the project classpath."
  :suggestions ["Verify you have not accidentally included two different versions of the same module."
                "Inspect the classpath to determine why the same module name is present in two different `arachne-modules.edn` files."]
  :ex-data-docs {:dup "The duplicated module name"
                 :definitions "The conflicing module definitions"})

(defn- validate-dependencies
  "Given a set of module definitions, throw an exception if all dependencies are
  not present, given a seq of module definitions. Also checks that a module is
  not defined twice, in different ways"
  [definitions]
  (let [names (map :arachne.module/name definitions)
        dup (first (keys (filter #(< 1 (second %)) (frequencies names))))]
    (when dup
      (let [dup-defs (filter #(= dup (:arachne.module/name %)) definitions)]
        (error ::dup-definition {:dup dup
                                   :definitions dup-defs})))
    (doseq [{:keys [:arachne.module/name :arachne.module/dependencies]
             :as definition} definitions]
      (let [missing (set/difference (set dependencies) (set names))]
        (when-not (empty? missing)
          (error ::missing-module {:module-name name
                                     :module definition
                                     :missing missing}))))))

(defn- as-loom-graph
  "Convert a seq of module definitions to a loom graph"
  [definitions]
  (let [by-name (zipmap (map :arachne.module/name definitions) definitions)
        graph (zipmap (keys by-name) (map :arachne.module/dependencies
                                       (vals by-name)))]
    (loom/digraph graph)))

(deferror ::circular-module-deps
  :message "Circular module dependencies"
  :explanation "Could not sort modules because module dependencies contains circular references. Module dependencies should form a directed acyclic graph, so that they have a strict sort order.")

(defn- topological-sort
  "Given a set of module defintions, return a seq of module definitions in
  depenency order. Throws an exception if a dependency is missing, or if there
  are cycles in the module graph."
  [definitions]
  (validate-dependencies definitions)
  (let [by-name (zipmap (map :arachne.module/name definitions) definitions)
        loom-graph (as-loom-graph definitions)
        sorted (loom-alg/topsort loom-graph)]
    (when (nil? sorted)
      (error ::circular-module-deps {:definitions definitions
                                     :graph loom-graph}))
    (map by-name (reverse sorted))))

(deferror ::module-def-did-not-conform
  :message "Invalid module module definition `:name`"
  :explanation "The module definition (found in the `arachne-module.edn` file) for the module `:name` did not conform to the required spec, `:arachne.core.module.specs/definition`"
  :suggestions ["Ensure that the module definition is correct and has all the required parts."]
  :ex-data-docs {:name "Name of the invalid module"})

(defn- validate-definition
  "Throw a friendly exception if the given module definition does not conform to
  a the module spec"
  [def]
  (e/assert :arachne.core.module.specs/definition def
    ::module-def-did-not-conform {:name (:arachne.module/name def)})
  def)

(defn- discover
  "Return a set of module definitions present in the classpath (defined in
  `arachne-modules.edn`
  files)"
  []
  (->> "arachne-modules.edn"
    (.getResources (.getContextClassLoader (Thread/currentThread)))
    enumeration-seq
    (map slurp)
    (map edn/read-string)
    (apply concat)
    (map validate-definition)
    set))

(deferror ::missing-roots
  :message "Could not find root modules `:missing` on classpath"
  :explanation "Arachne requires a set of \"root\" modules when it is initialized: it is these modules and their dependencies that define which modules are actually active. Arachne was started with `:modules`, but of those, the modules `:missing` could not be found on the classpath."
  :suggestions ["Makes sure you have configured your project's dependencies correctly in your build.boot or project.clj."
                "Make sure the module names are correct and typo-free"]
  :ex-data-docs {:modules "The requested set of modules"
                 :missing "Modules that could not be found on the CP"})

(defn- validate-missing-roots
  "Throw an error if a root is required that is not present in the given set of
  definitions"
  [roots definitions]
  (let [definition-names (set (map :arachne.module/name definitions))
        missing-roots (set/difference (set roots) definition-names)]
    (when-not (empty? missing-roots)
      (error ::missing-roots {:modules definition-names
                                :missing missing-roots}))))

(defn- reachable
  "Given a seq of module definitions and a set of root modules, return only
  module definitions reachable from one of the root modules"
  [definitions root-modules]
  (validate-missing-roots root-modules definitions)
  (let [graph (as-loom-graph definitions)
        reachable (set (mapcat #(loom-alg/pre-traverse graph %) root-modules))]
    (filter #(reachable (:arachne.module/name %)) definitions)))

(defn load
  "Return a seq of validated module definitions for all modules on the classpath
  that are required by the specified modules, in dependency order."
  [root-modules]
  (e/assert-args `load root-modules)
  (->> (reachable (discover) root-modules)
       (topological-sort)))

(defn schema
  "Given a module definition, invoke the module's schema function."
  [definition]
  (e/assert-args `schema definition)
  (let [v (u/require-and-resolve (:arachne.module/schema definition))]
    (@v)))

(defn configure
  "Given a module and a configuration value, invoke the module's configure
  function."
  [definition cfg]
  (e/assert-args `configure definition cfg)
  (let [v (u/require-and-resolve (:arachne.module/configure definition))]
    (@v cfg)))
