(ns ^:config arachne.core.provenance-test.script
  (:require [arachne.core.dsl :as a]
            [arachne.core.provenance-test.dep-a]))

(a/id :test/rt (a/runtime [:test/a]))
