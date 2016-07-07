(ns arachne.core.modules.specs
  (:require [clojure.spec :as s]))

(s/def :arachne.module/definition
  (s/keys :req [:arachne.module/name
                :arachne.module/schema
                :arachne.module/configure]
          :opt [:arachne.module/dependencies]))

(s/def :arachne.module/name (s/and keyword? namespace))
(s/def :arachne.module/schema (s/and symbol? namespace))
(s/def :arachne.module/configure (s/and symbol? namespace))
(s/def :arachne.module/dependencies (s/+ :arachne.module/name))
