(ns arachne.core.module.specs
  (:require [clojure.spec :as s]
            [arachne.core.config.specs :as config-specs]))

(s/def ::name (s/and keyword? namespace))
(s/def ::schema (s/and symbol? namespace))
(s/def ::configure (s/and symbol? namespace))
(s/def ::dependencies (s/coll-of ::name :min-count 1 :distinct true))

(s/def ::definition
  (s/keys :req [:arachne.module/name
                :arachne.module/schema
                :arachne.module/configure]
          :opt [:arachne.module/dependencies]))

(s/fdef arachne.core.module/load
  :args (s/cat :module-names (s/coll-of ::name))
  :ret (s/coll-of ::definition))

(s/fdef arachne.core.module/schema
  :args (s/cat :module-definition ::definition))

(s/fdef arachne.core.mdoules/configure
  :args (s/cat :module-definition ::definition
               :config ::config-specs/config))
