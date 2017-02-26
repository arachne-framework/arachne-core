(ns ^:config arachne.core.ns-config-test.script
  (:require [arachne.core.dsl :as a]
            [arachne.core.ns-config-test.dep-a]))

(a/id :test/rt (a/runtime [:test/a]))
