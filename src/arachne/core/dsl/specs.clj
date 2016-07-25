(ns arachne.core.dsl.specs
  (:require [clojure.spec :as s]))

(s/def ::id (s/and keyword? namespace))

(s/fdef arachne.core.dsl/runtime
  :args (s/cat :id ::id
               :roots (s/coll-of ::id :min-count 1)))

(s/def ::dependency-map (s/map-of ::id keyword?))
(s/def ::constructor-symbol (s/and symbol? namespace))

(s/fdef arachne.core.dsl/component
  :args (s/cat :id ::id
               :dependencies ::dependency-map
               :constructor ::constructor-symbol))