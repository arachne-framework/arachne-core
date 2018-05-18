(ns arachne.core.schema
  (:require [arachne.core.descriptor.schema :as s]))

(def schema
  "Schema for the Arachne core module"
  [(s/class :arachne/Component []
            "The definition of a component used to build and Arachne system at runtime (using the Component library)"
            :arachne.component/dependencies :many :optional :arachne/Component
            "The dependencies of a component."
            :arachne.component/constructor :one :required :clojure/Var
            "Function that returns an uninitialized instance of a component. The function may take 0-2 arguments, with the following behaviors:

         - 0 arguments: invoked with no arguments
         - 1 arguments: the entity map obtained by a wildcard pull on the component entity
         - 2 arguments: the config itself and entity ID of the component entity"
            :arachne.component/instance :one :optional :clojure/Var
            "The initial instance of a component.")

   (s/class :arachne.component/Dependency []
     "Entity describing the link from a component a dependent component"
     :arachne.component.dependency/entity :one :required :arachne/Component
     "Links a component dependency to another component entity."
     :arachne.component.dependency/key :one :optional :xsd/string
     "The key with which to inject a dependency (as a string). If omitted, the key will default to the iri of the dependency.")

   (s/class :arachne/Runtime []
    "Entity describing a particular Arachne runtime"
    :arachne.runtime/components :many :required :arachne/Component
    "Top level components that constitute this runtime.")])
