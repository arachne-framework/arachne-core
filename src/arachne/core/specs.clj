(ns arachne.core.specs
  (:require [clojure.spec :as s]
            [arachne.core.config.specs :as cfg-spec]
            [arachne.core.module.specs :as ms]
            [arachne.core.util :as util]))

(s/fdef arachne.core/build-config
  :args (s/cat :modules (s/coll-of ::ms/name :min-count 1)
               :initializer (s/or :edn-file string?
                                  :form list?
                                  :txdata (s/and coll? not-empty))))
(s/fdef arachne.core/runtime
  :args (s/cat :config ::cfg-spec/config
               :arachne-id (s/and keyword? namespace)))