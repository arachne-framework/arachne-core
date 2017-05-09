(ns ^:config arachne.core.ns-config-test.dep-b
  (:require [arachne.core.dsl :as a]
            [arachne.core.ns-config-test.dep-c]))

(a/id :test/b (a/component 'some/function))
