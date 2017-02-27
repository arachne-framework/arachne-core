(ns arachne.core.ns-config-test.dep-c
  (:require [arachne.core.dsl :as a]))

(if (resolve 'only-once)
  (throw (ex-info "This namespace should only be required once" {})))

(def only-once 42)
