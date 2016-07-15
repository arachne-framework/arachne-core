(ns arachne.core.runtime.specs
  (:require [clojure.spec :as s]
            [arachne.core.util :as util]))

(def runtime? (util/lazy-instance? arachne.core.runtime.ArachneRuntime))

(s/fdef arachne.core.runtime/init
  :args (s/cat :config :arachne.core.config.specs/config
               :roots (s/coll-of
                        (s/or :eid :arachne.core.config.specs/entity-id
                              :lookup-ref :arachne.core.config.specs/lookup-ref)
                        :min-count 1))
  :ret runtime?)


(s/fdef arachne.core.runtime/lookup
  :args (s/cat :runtime runtime?
               :entity-ref
               (s/or :eid :arachne.core.config.specs/entity-id
                     :lookup-ref :arachne.core.config.specs/lookup-ref)))