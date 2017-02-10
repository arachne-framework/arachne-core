(ns arachne.core.dsl
  "User-facing DSL for use in init scripts"
  (:require [arachne.core.config :as cfg]
            [arachne.core.config.script :as script :refer [defdsl]]
            [arachne.core.config.specs :as cfg-specs]
            [arachne.core.util :as util]
            [clojure.spec :as s])
  (:refer-clojure :exclude [ref]))

(s/def ::arachne-id qualified-keyword?)
(s/def ::entity-id pos-int?)
(s/def ::tempid #(instance? arachne.core.config.Tempid %))

(s/def ::ref (s/or :aid ::arachne-id
                   :eid ::entity-id
                   :tid ::tempid))

(defn ^:no-doc resolved-ref
  "Given the conformed value of a ::ref spec, return a concrete entity ID, throwing an error if it cannot be found."
  [[type ref]]
  (case type
    :aid (script/resolve-aid ref)
    ref))

(defn ^:no-doc ref
  "Given the conformed value of a ::ref spec, return txdata to identify the entity. The returned
   txdata is suitable for use as a ref value in an entity map. Arachne IDs do not need to have been
   previously used in the context config."
  [[type ref]]
  (case type
    :aid {:arachne/id ref}
    ref))

(defdsl transact
  "Update the context configuration with the given txdata. If a tempid is provided as an optional
   second argument, then the resolved entity ID will be returned, otherwise nil."
  (s/cat :txdata ::cfg-specs/txdata :tempid (s/? ::tempid))
  [txdata & [tempid]]
  (script/transact txdata tempid))

(defdsl runtime
  "Defines a named Arachne runtime containing the given root components.

   Returns the entity ID of the runtime."
  (s/cat :id ::arachne-id
         :roots (s/coll-of ::ref :min-count 1))
  [id roots]
  (let [tid (cfg/tempid)
        txdata [{:db/id tid
                 :arachne/id id
                 :arachne.runtime/components (map ref (:roots &args))}]]
    (script/transact txdata tid)))

(s/def ::constructor qualified-symbol?)
(s/def ::dependency-map (s/map-of keyword? ::ref :min-count 1))

(defdsl component
  "Low-level form for defining a component. Requires an Arachne ID (optional), the full-qualified name of a
   component constructor function (mandatory), and a map of dependencies (optional).

   For example:

      (component :my/some-component 'my.app/ctor)

   Or:

      (component :my/other-component 'my.app/ctor {:foobar :my/some-component})

  Returns the entity ID of the component.

  In general, an Arachne ID should always be provided, so the component can be referenced, unless
  the `component` form is being nested in another DSL form and its returned eid is captured."
  (s/cat :arachne-id  (s/? ::arachne-id)
         :constructor ::constructor
         :dependencies (s/? ::dependency-map))
  [<arachne-id> constructor <dependency-map>]
  (let [tid (cfg/tempid)
        entity (util/mkeep
                 {:db/id tid
                  :arachne/id (:arachne-id &args)
                  :arachne.component/constructor (keyword (:constructor &args))})
        txdata (map (fn [[k v]]
                      {:db/id tid
                       :arachne.component/dependencies
                       [{:arachne.component.dependency/key k
                         :arachne.component.dependency/entity (ref v)}]})
                 (:dependencies &args))
        txdata (conj txdata entity)]
    (script/transact txdata tid)))
