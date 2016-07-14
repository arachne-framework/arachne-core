(ns arachne.core.config.specs
  (:require [clojure.spec :as s]
            [arachne.core.util :as u]))

(def config? (u/lazy-satisfies? arachne.core.config/Configuration))
(def tempid? (u/lazy-instance? arachne.core.config.Tempid))
(def date? (u/lazy-instance? java.util.Date))

(s/def ::config config?)

(s/def ::attribute (s/and keyword? namespace))

(s/def ::entity-id (s/or :eid pos-int?
                         :tempid tempid?))

(s/def ::scalar (s/or :number number?
                      :string string?
                      :date date?
                      :boolean boolean?
                      :keyword keyword?
                      :uuid uuid?
                      :uri uri?
                      :bytes bytes?))

(s/def ::lookup-ref (s/tuple ::attribute ::scalar))

(s/def ::value (s/or :scalar ::scalar
                     :entity-id ::entity-id
                     :lookup ::lookup-ref
                     :collection (s/coll-of ::value :min-count 1)))

(s/def ::list-txform (s/tuple
                       #{:db/add :db/retract}
                       ::entity-id
                       ::attribute
                       ::value))

(s/def ::map-txform (s/map-of ::attribute (s/or :value ::value
                                                :nested ::map-txform)))

(s/def ::txdata (s/coll-of (s/or :list ::list-txform
                                 :map ::map-txform)
                           :min-count 1))

(s/fdef arachne.core.config/init
  :args (s/cat :config ::config, :schema-txes (s/coll-of ::txdata :min-count 1))
  :ret ::config)

(s/fdef arachne.core.config/update
  :args (s/cat :config ::config, :txdata ::txdata)
  :ret ::config)