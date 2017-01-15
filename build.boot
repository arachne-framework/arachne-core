(set-env! :repositories
  #(conj % ["arachne-dev" {:url "http://maven.arachne-framework.org/artifactory/arachne-dev"}]))

(set-env!
  :dependencies
  `[[org.arachne-framework/arachne-buildtools "0.3.1-master-0038-ee4800c" :scope "test"]])

(require '[arachne.buildtools :refer :all])

(read-project-edn!)