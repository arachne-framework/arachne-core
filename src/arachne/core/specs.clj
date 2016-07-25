(ns arachne.core.specs
  (:require [clojure.spec :as s]
            [arachne.core.module.specs :as ms]
            [arachne.core.util :as util]))

(s/fdef arachne.core/build-config
  :args (s/cat :config-ns string?
               :root-modules (s/coll-of ::ms/name :min-count 1)
               :initializer (s/or :edn-file string?
                                  :form list?
                                  :txdata (s/and coll? not-empty))))
