(config (:require [arachne.core.dsl :as a]))

(a/id :test/rt (a/runtime [:test/component]))

(a/id :test/component (a/component 'arachne-test.core/test-component))
