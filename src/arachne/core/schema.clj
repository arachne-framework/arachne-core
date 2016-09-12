(ns arachne.core.schema
  (:require [arachne.core.config :refer [tempid]]
            [arachne.core.config.ontology :as o]))

(def schema
  "Schema for the Arachne core module"
  (concat

    [(o/attr :arachne/id :one-or-none :keyword :identity
       "Unique identifier for an entity in an Arachne configuration"
       {:arachne.attribute/domain {:db/ident :arachne/Entity}})

     (o/attr :arachne.class/component-spec :many :keyword
       "Spec to validate runtime instances of components that are instances of this class."
       {:arachne.attribute/domain {:db/ident :arachne/Class}})]

    (o/class :arachne/Component []
      "The definition of a component used to build and Arachne system at runtime (using the Component library)"
      (o/attr :arachne.component/dependencies
        :many :arachne.component/Dependency
        "The dependencies of a component.")
      (o/attr :arachne.component/constructor :one :keyword
        "Namespaced keyword indicating the fully-qualified name of a function that returns an uninitialized instance of a component. The function may take 0-2 arguments, with the following behaviors:

         - 0 arguments: invoked with no arguments
         - 1 argument:  the entity map obtained by a wildcard pull on the component entity
         - 2 argumetns: the config itself and entity ID of the component entity")
      (o/attr :arachne.component/instance :one-or-none :keyword
        "Namespaced keyword indicating the fully-qualified name of a var which is the initial instance of the component."))

    (o/class :arachne.component/Dependency []
      "Entity describing the link from a component a dependent component"
      (o/attr :arachne.component.dependency/entity :one :arachne/Component
        "Links a component dependency to another component entity.")
      (o/attr :arachne.component.dependency/key :one-or-none :keyword
        "The key with which to inject a dependency. If omitted, the key will default to the keyword-ified entity ID of the dependency."))

    (o/class :arachne/Runtime []
      "Entity describing a particular Arachne runtime"
      (o/attr :arachne.runtime/components :one-or-more :arachne/Component
        "Top-level components that constitute this runtime"))

    (o/class :arachne/Configuration []
      "Entity representing an entire configuration"
      (o/attr :arachne.configuration/validators :many :keyword
        "Validator functions for this configuration, as a namespace-qualified keyword. Validator functions take a config and either return successfully or throw an exception.")
      (o/attr :arachne.configuration/namespace :one-or-none :keyword :identity
        "The unique identifier of the configuration, as a non-namespaced keyword. Should be set and used as the `namespace` portion of all Arachne IDs for entities that are logically part of this configuration, in scenarios where multiple configurations are present in the same database.")
      (o/attr :arachne.configuration/roots :one-or-more :arachne/Entity
        "Reference to the top-level entities that are part of this configuration."))

    (o/class :arachne/Transaction []
      "Entity reifying a single transaction to the config"
      (o/attr :arachne.transaction/source :one-or-none :keyword
        "Keyword that indicates the source of this config txdata. Valid values are :user (for the initial user-supplied configuration), :module (for txdata built from modules or :system (for bookkeeping data used by Arachne itself). :test is also permitted in tests, to supress the warning about a lack of provenance data.")
      (o/attr :arachne.transaction/source-file :one-or-none :string
        "The filename of the source file (possibly a DSL script) containing the code that initiated this transaction.")
      (o/attr :arachne.transaction/source-line :one-or-none :long
        "The line number in source file that initated this transaction.")
      (o/attr :arachne.transaction/function :one-or-none :keyword
        "Keyword representation of the fully-qualified name of the function used to construct this transaction.")

      )

    ))
