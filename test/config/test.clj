(require '[arachne.core.dsl :as a])

(a/runtime :test/rt [:test/component])

(a/component :test/component 'arachne-test.core/test-component)
