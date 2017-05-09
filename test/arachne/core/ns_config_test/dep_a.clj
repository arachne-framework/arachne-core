(ns ^:config arachne.core.ns-config-test.dep-a
  (:require [arachne.core.dsl :as a]
            [arachne.core.ns-config-test.dep-b]))

(a/id :test/a (a/component 'some/function {:b :test/b}))
