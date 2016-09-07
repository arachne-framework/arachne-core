(require '[arachne.core.dsl :as dsl])

(dsl/runtime :test/rt [:test/a])

(dsl/component :test/a {}
  'arachne.core.provenance-test/test-ctor)