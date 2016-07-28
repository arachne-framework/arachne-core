(def buildtools-version (System/getenv "ARACHNE_BUILDTOOLS_VERSION"))
(when-not buildtools-version
  (throw (ex-info "Build requires an ARACHNE_BUILDTOOLS_VERSION environment variable" {})))

(set-env!
  :dependencies
  `[[org.arachne-framework/arachne-buildtools ~buildtools-version :scope "test"]])

(require '[arachne.buildtools :refer :all])

(read-project-edn!)

(require '[adzerk.boot-test :refer [test]])
