(ns arachne.core
  "The core Arachne module that bootstraps everything else"
  (:require [arachne.core.module :as m]
            [arachne.core.config :as cfg]
            [arachne.core.config.validation :as v]
            [arachne.core.runtime :as rt]
            [arachne.core.config.init :as init]
            [arachne.core.util :as util]
            [arachne.core.schema :as schema]
            [arachne.core.specs]
            [arachne.error :as e]))

(defn instance-ctor
  "Component constructor that defines components by resolving a var"
  [component]
  @(util/require-and-resolve (:arachne.component/instance component)))

(defn- add-instance-constructors
  "Implement :arachne.component/instance in terms of :arachne.component/constructor"
  [cfg]
  (let [components (cfg/q cfg '[:find [?c ...]
                                :where
                                [?c :arachne.component/instance ?var]])]
    (if (empty? components)
      cfg
      (cfg/with-provenance :module `add-instance-constructors
        (cfg/update cfg
          (for [c components]
            {:db/id c
             :arachne.component/constructor :arachne.core/instance-ctor}))))))

(defn schema
  "Return the schema for the core module"
  []
  schema/schema)

(defn configure
  "Configure the core module"
  [cfg]
  (-> cfg
    (add-instance-constructors)
    (v/add-core-validators)))

(defn build-config
  "Build a new Arachne application configuration. Requires two arguments:

  - A collection of modules that will participate in building the configuration.
  The modules can include their own dependencies, so you only need to
  explicitly declare the top-level modules you want to use.

  - A config initializer, containing the configuration data that you supply.
  This can be:
    - A string, which will be interpreted as the path to an initialization script
    - A list, which will be evaluated as an initialization script
    - A vector, which will be assumed to contain raw confgiuration data as a
    Datomic-style transaction.

    Validates the config before returning."
  ([modules initializer]
   (build-config modules initializer true))
  ([modules initializer throw-validation-errors?]
   (e/assert-args `build-config modules initializer)
   (let [module-definitions (m/load modules)
         cfg (init/initialize module-definitions initializer)
         cfg (reduce (fn [c m] (m/configure m c))
               cfg (reverse module-definitions))]
     (v/validate cfg throw-validation-errors?))))

(defn runtime
  "Create a new Arachne runtime from the given configuration and the :arachne/id
  of the root runtime entity"
  [cfg arachne-id]
  (e/assert-args `runtime cfg arachne-id)
  (rt/init cfg [:arachne/id arachne-id]))