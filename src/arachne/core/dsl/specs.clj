(ns arachne.core.dsl.specs
  (:require [clojure.spec :as s]))

(s/def ::id (s/and keyword? namespace))
(s/def ::eid pos-int?)

(s/def ::identity (s/or :id ::id :eid ::eid))

(s/fdef arachne.core.dsl/runtime
  :args (s/cat :id ::id
               :roots (s/coll-of ::identity :min-count 1)))

(s/def ::dependency-map (s/map-of ::identity keyword?))

(s/def ::constructor-symbol (s/and symbol? namespace))

(s/fdef arachne.core.dsl/component
  :args (s/cat :arachne-id (s/? ::id)
               :dependencies (s/? ::dependency-map)
               :constructor ::constructor-symbol))