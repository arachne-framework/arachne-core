(ns ^:config arachne.core.provenance-test.dep-a
  (:require [arachne.core.dsl :as a]))

(a/id :test/a (a/component 'arachne.core.provenance-test/test-ctor))
