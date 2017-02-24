(config (:require [arachne.core.dsl :as a]))

(a/id :test/rt (a/runtime [:test/a]))

(a/id :test/a (a/component 'arachne.core.provenance-test/test-ctor))