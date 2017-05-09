(ns arachne.core.dsl
  "User-facing DSL for use in init scripts"
  (:require [arachne.core.config :as cfg]
            [arachne.core.config.script :as script :refer [defdsl]]
            [arachne.core.config.specs :as cfg-specs]
            [arachne.core.util :as util]
            [clojure.spec.alpha :as s])
  (:refer-clojure :exclude [ref def]))

(s/def ::arachne-id qualified-keyword?)
(s/def ::entity-id pos-int?)
(s/def ::tempid #(instance? arachne.core.config.Tempid %))

(s/def ::ref (s/or :aid ::arachne-id
                   :eid ::entity-id
                   :tid ::tempid))

(defn ^:no-doc reified-ref
  "Return a txdata map for a Reified Ref entity referring to the specified entity ID.

  Reified Refs provide a way to reference other entities in a configuration, even if they may not
  have been created yet.

  They are resolved and replaced with their referent at the beginning of the module Configure
  phase. If a referent cannot be found in a completed config when the Configure phase begins, an
  error will be thrown."
  [arachne-id]
  {:arachne.reified-reference/attr :arachne/id
   :arachne.reified-reference/value arachne-id})

(defn ^:no-doc ref
  "Given the conformed value of a ::ref spec, return txdata to identify the entity. The returned
   txdata is suitable for use as a ref value in an entity map. Arachne IDs will be replaced with a
   Reified Reference (see `reified-ref`)."
  [[type ref]]
  (case type
    :aid (reified-ref ref)
    ref))

(defn ^:no-doc ref-txmap
  "Given the conformed value of a ::ref spec, return map txdata.

   The map will have a :db/id attribute, referring to either a concrete entity
   ID or the tempid of a reified ref."
  [[type ref]]
  (case type
    :aid (assoc
          (reified-ref ref)
           :db/id (cfg/tempid))
    {:db/id ref}))

(defdsl transact
  "Update the context configuration with the given txdata. If a tempid is provided as an optional
   second argument, then the resolved entity ID will be returned, otherwise nil."
  (s/cat :txdata ::cfg-specs/txdata :tempid (s/? ::tempid))
  [txdata & [tempid]]
  (script/transact txdata tempid))

(defdsl id
  "Declare an Arachne ID for an entity.

  Typically used to immediately capture an entity yeilded by a DSL form, and assign it an entity
  ID. For example:

      (def :my/thing (component 'my/constructor))

  Optionally takes a docstring as well, which will be added to the entity under the `:arachne/doc`
  attribute. For example:

      (def :my/thing
         \"The main thing that the other things use to do the thing\"
         (component 'my/constructor))

  Returns the entity ID."
  (s/cat :arachne-id ::arachne-id
         :docstr (s/? string?)
         :entity-id ::entity-id)
  [arachne-id <docstr> entity]
  (script/transact [(util/mkeep
                      {:db/id (:entity-id &args)
                       :arachne/id (:arachne-id &args)
                       :arachne/doc (:docstr &args)})])
  (:entity-id &args))

(defdsl runtime
  "Defines an Arachne runtime containing the given root components.

   Returns the entity ID of the runtime."
  (s/cat :roots (s/coll-of ::ref :min-count 1))
  [roots]
  (let [tid (cfg/tempid)
        txdata [{:db/id tid
                 :arachne.runtime/components (map ref (:roots &args))}]]
    (script/transact txdata tid)))

(s/def ::constructor qualified-symbol?)
(s/def ::dependency-map (s/map-of keyword? ::ref :min-count 1))

(defdsl component
  "Low-level form for defining a component. Takes the fully-qualified name of a component
   constructor function (mandatory), and a map of dependencies (optional).

   For example:

      (component 'my.app/ctor)

   Or:

      (component 'my.app/ctor {:foobar :my/some-component})

  Returns the entity ID of the component."
  (s/cat :constructor ::constructor
         :dependencies (s/? ::dependency-map))
  [constructor <dependency-map>]
  (let [tid (cfg/tempid)
        entity (util/mkeep
                 {:db/id tid
                  :arachne.component/constructor (keyword (:constructor &args))})
        txdata (map (fn [[k v]]
                      {:db/id tid
                       :arachne.component/dependencies
                       [{:arachne.component.dependency/key k
                         :arachne.component.dependency/entity (ref v)}]})
                 (:dependencies &args))
        txdata (conj txdata entity)]
    (script/transact txdata tid)))

(defn enable-debug!
  "Globally enable DSL script debugging. This allows you to evaluate DSL forms outside the context of a
   config script (for example, by themselves at the REPL.)

   However, instead of updating a configuration (since there is none to update), any transactions
   are simply pretty-printed to System/out instead. This is useful for debugging DSL forms and
   understanding how they map to data in the configuration.

   DSL forms which normally woudl return a resolved entity ID will instead return a random
   integer, allowing nested DSL forms to be debugged."
  []
  (alter-var-root #'script/*debug-dsl-txdata* (constantly true)))

(defn disable-debug!
  "Globally disable DSL script debugging."
  []
  (alter-var-root #'script/*debug-dsl-txdata* (constantly false)))
