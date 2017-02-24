(ns arachne.core.module
  "The namespace used for defining and loading Arachne modules"
  (:refer-clojure :exclude [load sort])
  (:require [clojure.edn :as edn]
            [clojure.set :as set]
            [clojure.spec :as s]
            [loom.graph :as loom]
            [loom.alg :as loom-alg]
            [clj-fuzzy.levenshtein :as lev]
            [arachne.core.config :as cfg]
            [arachne.core.config.script :as script]
            [arachne.core.config.validation :as v]
            [arachne.error :as e :refer [error deferror]]
            [arachne.error.format :as efmt]
            [arachne.core.util :as u]))

(s/def :arachne/name qualified-keyword?)
(s/def :arachne/schema qualified-symbol?)
(s/def :arachne/configure qualified-symbol?)
(s/def :arachne/dependencies (s/coll-of :arachne/name :min-count 1 :distinct true))
(s/def :arachne/init (s/or :filename string?
                           :symbol symbol?
                           :vector vector?
                           :literal seq?))
(s/def :arachne/inits (s/coll-of :arachne/init :min-count 1))

(s/def ::definition
  (s/keys :req [:arachne/name]
          :opt [:arachne/dependencies
                :arachne/schema
                :arachne/configure
                :arachne/inits]))

(deferror ::missing-module
  :message "Modules `:missing` not found"
  :explanation "Module `:module-name` declared a dependency on modules `:missing`. However, these modules could not be found on the classpath. Modules are defined in `arachne.edn` files that must be on the classpath in order for the module to be discovered."
  :suggestions ["Makes sure you have configured your project's dependencies correctly in your build.boot or project.clj."
                "Make sure the module names are correct and typo-free"]
  :ex-data-docs {:module-name "Name of the module with a missing dep"
                 :module "Module definition with the missing dep "
                 :missing "Name of missing module"})

(deferror ::dup-definition
  :message "Duplicate module definition for `:dup`"
  :explanation "Found two definitions for module named `:dup`, and the definitions were not the same. This can happen when two different versions of the module are somehow both on the project classpath."
  :suggestions ["Verify you have not accidentally included two different versions of the same module."
                "Inspect the classpath to determine why the same module name is present in two different `arachne.edn` files."]
  :ex-data-docs {:dup "The duplicated module name"
                 :definitions "The conflicing module definitions"})

(defn- validate-dependencies
  "Given a set of module definitions, throw an exception if all dependencies are
  not present, given a seq of module definitions. Also checks that a module is
  not defined twice, in different ways"
  [definitions]
  (let [names (map :arachne/name definitions)
        dup (first (keys (filter #(< 1 (second %)) (frequencies names))))]
    (when dup
      (let [dup-defs (filter #(= dup (:arachne/name %)) definitions)]
        (error ::dup-definition {:dup dup
                                 :definitions dup-defs})))
    (doseq [{:keys [:arachne/name :arachne/dependencies]
             :as definition} definitions]
      (let [missing (set/difference (set dependencies) (set names))]
        (when-not (empty? missing)
          (error ::missing-module {:module-name name
                                   :module definition
                                   :missing missing}))))))

(defn- as-loom-graph
  "Convert a seq of module definitions to a loom graph"
  [definitions]
  (let [by-name (zipmap (map :arachne/name definitions) definitions)
        graph (zipmap (keys by-name) (map :arachne/dependencies (vals by-name)))]
     (loom/digraph graph)))

(deferror ::circular-module-deps
  :message "Circular module dependencies"
  :explanation "Could not sort modules because module dependencies contains circular references. Module dependencies should form a directed acyclic graph, so that they have a strict sort order.")

(defn- topological-sort
  "Given a seq of module defintions, return a seq of module definitions in
  depenency order. Throws an exception if a dependency is missing, or if there
  are cycles in the module graph."
  [definitions]
  (validate-dependencies definitions)
  (let [by-name (zipmap (map :arachne/name definitions) definitions)
        loom-graph (as-loom-graph definitions)
        sorted (loom-alg/topsort loom-graph)]
    (when (nil? sorted)
      (error ::circular-module-deps {:definitions definitions
                                     :graph loom-graph}))
    (map by-name (reverse sorted))))

(defn- reachable
  "Given a seq of module definitions and a single root definition, return only those module
   definitions reachable from the root module."
  [all-definitions definition]
  (let [graph (as-loom-graph all-definitions)
        reachable-names (set (loom-alg/pre-traverse graph (:arachne/name definition)))]
    (filter #(reachable-names (:arachne/name %)) all-definitions)))

(deferror ::module-def-did-not-conform
  :message "Invalid Arachne module definition `:name`"
  :explanation "The module definition (found in the `arachne.edn` file) for the module `:name` did not conform to the required spec, `:arachne.core.module/definition`"
  :suggestions ["Ensure that the module definition is correct and has all the required parts."]
  :ex-data-docs {:name "Name of the invalid module"})

(defn- validate-definition
  "Throw a friendly exception if the given module definition does not conform to
  a the module spec"
  [def]
  (e/assert ::definition def
    ::module-def-did-not-conform {:name (:arachne/name def)})
  def)


(defn- discover-definitions
  "Return a set of module and application definitions present in the classpath (defined in `arachne.edn` files)"
  []
  (->> "arachne.edn"
    (.getResources (.getContextClassLoader (Thread/currentThread)))
    enumeration-seq
    (map slurp)
    (map edn/read-string)
    (apply concat)
    (map validate-definition)
    set))

(defn- schema
  "Given a module definition, return schema for the module (if present), using the modules' schema
   function. Otherwise returns nil."
  [definition]
  (when (:arachne/schema definition)
    (let [schema-fn (deref (u/require-and-resolve (:arachne/schema definition)))]
      (schema-fn))))

(deferror ::error-in-initializer
  :message "Error initializing module `:name`"
  :explanation "Every Arachne module (including your application itself) has the opportunity to define some initial data in the configuration, after the config has been created and the config schema is installed, but before the module configuration phase. These initializers are atteched to the module definition in `arachne.edn`, under the `:arachne/inits` key.

   For user applications, this is when your config DSL scripts are applied.

   However, while applying the initializers for a module or application named `:name`, an exception was thrown.

   The initializer or script in question is:

       :initializer-str
   "
  :suggestions ["Inspect this exception's cause to see the actual error that occurred."
                "Make sure you have specified the correct config DSL script, and that it is error-free."]
  :ex-data-docs {:definition "the module definition"
                 :name "the module name"
                 :initializer "The failed script (or other initializer)"
                 :initializer-str "The initializer, pprinted and truncated"})

(defn- initialize
  "Given a module definition, apply its initializer script(s) to the given config."
  [cfg definition]
  (reduce (fn [cfg initializer]
            (try
              (script/apply-initializer cfg initializer)
              (catch Throwable t
                (error ::error-in-initializer {:definition definition
                                               :name (:arachne/name definition)
                                               :initializer-str (efmt/pprint-str-truncated initializer 5)
                                               :initializer initializer} t))))
    cfg
    (:arachne/inits definition)))


(deferror ::error-in-configure
  :message "Error in module configure phase for `:name` module"
  :explanation "Modules can have a 'configure' phase in which they have an opportunity to make arbitrary updates to the configuration as it is constructed. This is defined by a function specified using `:arachne/configure` in the module definition, in its `arachne.edn` file. These configure functions are called in reverse dependency order.

  In this case, the function `:fn`, specified by the module `:name` threw an exception."
  :suggestions ["Inspect the cause of this exception to see the original error thrown from `:fn`"]
  :ex-data-docs {:definition "the module definition"
                 :name "the module name"
                 :fn "the configure function that errored"})

(defn- configure
  "Given a module definition, apply its configure phase to the given config (if it has one)"
  [cfg definition]
  (if-not (:arachne/configure definition)
    cfg
    (let [configure-fn (deref (u/require-and-resolve (:arachne/configure definition)))]
      (try
        (configure-fn cfg)
        (catch Throwable t
          (error ::error-in-configure {:definition definition
                                       :name (:arachne/name definition)
                                       :fn (:arachne/configure definition)} t))))))


(deferror ::unresolved-ref
  :message "Reference to `:value` not found in the config.:best-guess-msg-str"
  :explanation "The configuration referenced an entity that had a value of `:value` for its identity attribute `:attr`. However, no such identity could be found in the config.

  :candidates-str"
  :suggestions ["Fix any typos in the reference name"
                "Ensure that the referenced entity exists in your configuration"]
  :ex-data-docs {:value "The missing identity"
                 :attr "The identity attribute"
                 :best-guess-msg-str "Formatted string of the best guess"
                 :candidates "Possiblities by Levenshtein distance"
                 :similar-values-str "Formatted string list of candidates"
                 :cfg "The configuration"})

(defn- missing-reference-error
  "Throw a missing reference error"
  [cfg entity attr rr rr-attr rr-value tx]
  (let [possible-values (cfg/q cfg '[:find [?v ...]
                                     :in $ ?attr
                                     :where [_ ?attr ?v]] rr-attr)
        candidates (->> possible-values
                     (map #(vector % (lev/distance (str rr-value) (str %))))
                     (filter (fn [[s dist]] (< dist (/ (count (str s)) 3))))
                     (sort-by second)
                     (take 3))
        [[best best-dist] & _] candidates]
    (error ::unresolved-ref {:attr rr-attr
                             :value rr-value
                             :cfg cfg
                             :candidates candidates
                             :candidates-str (if (seq candidates)
                                               (format "Some similar values in the config:\n\n%s\n\nDid you mean to type one of these instead?"
                                                 (e/bullet-list
                                                   (map (fn [[str dist]]
                                                          (format "`%s` (edit distance: %s)" str dist)) candidates)))
                                               "")
                             :best-guess-msg-str (when (and (number? best-dist) (< best-dist 5))
                                                   (format " Did you mean `%s`?" best))})))

(defn- original-tx-provenance
  "Return an entity map of the reproducible portion of the original transaction's provenance
   metadata"
  [cfg original-tx]
  [(assoc
    (cfg/pull cfg '[:arachne.transaction/source-file
                    :arachne.transaction/source-line
                    :arachne.transaction/function
                    :arachne.transaction/source]
      original-tx)
     :db/id (cfg/tempid :db.part/tx))])

(defn- resolve-reified-reference
  "Resolve a reified reference and return a config with the value replaced. Throw an exception if
   the value can't be found."
  [cfg [entity attr rr rr-attr rr-value tx]]
  (let [replacement (cfg/q cfg '[:find ?e .
                                 :in $ ?attr ?val
                                 :where [?e ?attr ?val]]
                      rr-attr rr-value)]
    (if replacement
      (cfg/update cfg
        (concat [[:db/retract entity attr rr]
                 [:db/add entity attr replacement]]
          (original-tx-provenance cfg tx)))
      (missing-reference-error cfg entity attr rr rr-attr rr-value tx))))

(defn- resolve-reified-references
  "Replace all ReifiedReference entities in the config with a direct entity reference. Throws an
   error if a reference cannot be found."
  [cfg]
  (let [rrefs (cfg/q cfg '[:find ?entity ?attr-ident ?rr ?rr-attr ?rr-value ?tx
                           :where
                           [?rr :arachne.reified-reference/attr ?rr-attr]
                           [?rr :arachne.reified-reference/value ?rr-value]
                           [?entity ?attr ?rr ?tx]
                           [?attr-eid :db/ident ?attr]
                           [?attr-eid :db/ident ?attr-ident]
                           ])]
    (reduce resolve-reified-reference cfg rrefs)))

(defn- config*
  "Build a config given a concrete definition map"
  [blank-cfg all-definitions definition throw-validation-errors?]
  (let [active-definitions (reachable all-definitions definition)
        active-definitions (topological-sort active-definitions)
        ;; Schema and initial config construction
        cfg (cfg/init blank-cfg (keep schema active-definitions))
        ;; Initialize phase: dependency order
        cfg (reduce initialize cfg active-definitions)
        ;; At this point, resolve all ReifiedReference entities
        cfg (resolve-reified-references cfg)
        ;; Configure phase: reverse dependency order
        cfg (reduce configure cfg (reverse active-definitions))]
    (v/validate cfg throw-validation-errors?)))

(deferror ::module-name-not-found
  :message "Could not find module named `:missing` on classpath"
  :explanation "Arachne requires you to specify a module/application name when you first initialize a config; it is this module and its dependencies that define which modules are active.

   In this case, the module :missing was specified, but no module with that name could be found in any `arachne.edn` file on the classpath."
  :suggestions ["Makes sure you have configured your project's dependencies correctly in your build.boot or project.clj"
                "Make sure the application and module names are correct and typo-free"]
  :ex-data-docs {:missing "The missing module name"})

(deferror ::module-already-declared
  :message "Module named `:name` already declared on classpath."
  :explanation "Arachne's configuration construction function, `arachne.core/config`, allows you to pass either the name of a module or a module definition map.

  In this case, it was passed a module definition map with an `:arachne/name` of `:name`, but that module is already defined in an `arachne.edn` file on the classpath. To prevent unexpected behavior, this is not allowed."
  :suggestions ["Choose a different name for the module."
                "Pass just the name of the module and use the definition from the classpath instead."]
  :ex-data-docs {:name "The module name"})

(defn ^:no-doc config
  "Define a configuration. Intended to be called through arachen.core/config."
  [module blank-cfg throw-validation-errors?]
  (let [named? (keyword? module)
        name (if named? module (:arachne/name module))
        classpath-definitions (discover-definitions)
        existing (first (filter #(= name (:arachne/name %)) classpath-definitions))]
    (when (and named? (not existing))
      (error ::module-name-not-found {:missing name}))
    (when (and (not named?) existing)
      (error ::module-already-exists {:name name}))
    (let [definition (if named? existing module)
          all-definitions (if named? classpath-definitions (conj classpath-definitions module))]
      (config* blank-cfg all-definitions definition throw-validation-errors?))))
