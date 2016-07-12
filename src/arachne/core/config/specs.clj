(ns arachne.core.config.specs
  (:require [clojure.spec :as s]
            [arachne.core.util :as u]))

(def config? (u/satisfies-pred arachne.core.config/Configuration))

(s/def ::config config?)