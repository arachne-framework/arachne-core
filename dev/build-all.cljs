#!/usr/bin/env planck
(ns build-all.main
  (:require [planck.shell :refer [sh]]
            [planck.io :as io]
            [planck.core :as core :refer [*command-line-args*]]
            [planck.http :as http]
            [cljs.reader :as reader]
            [clojure.string :as str]
            [clojure.string :as str]))

(def new-deps (atom {}))

(def dirs
  ["arachne-core"
   "arachne-http"
   "arachne-pedestal"])

(defn bail [& msgs]
  (apply println msgs)
  (core/exit 1))

(defn run-tests [dir]
  (println "running tests for" dir)
  (let [result (sh "boot" "test" :dir dir)]
    (when (not= 0 (:exit result))
      (bail "...tests failed"))
    (println "...tests passed")))

(defn ensure-clean-git [dir]
  (let [result (:out (sh "git" "status" :dir dir))]
    (when-not (re-find #"On branch master" result)
      (bail "exiting," dir "not on master branch"))
    (when-not (re-find #"nothing to commit, working tree clean" result)
      (bail "exiting," dir "is not clean"))))

(defn maybe-replace-deps [dir]
  (println "updating dependencies for" dir)
  (let [file (str dir "/project.edn")
        project (core/slurp file)
        deps (:deps (reader/read-string project))
        to-replace (filter #(@new-deps (first %)) deps)
        updated (reduce (fn [project [name version & _]]
                          (let [new-version (@new-deps name)
                                search-re (re-pattern (str name "[\\s]+" "\"[^\\s]+\""))
                                new (str name " " "\"" new-version "\"")]
                            (if (= version new-version)
                              project
                              (do
                                (println "...updating" name "from" version "to" new-version)
                                (str/replace project search-re new)))))
                  project
                  to-replace)]
    (if (not= project updated)
      (do
        (println "...checking in updated deps")
        (core/spit file updated)
        (run-tests dir)
        (sh "git" "commit" "-am" "update deps" :dir dir))
      (println "...deps are unchanged"))))

;; Installed Version: 0.1.0-master-0083-7e058f6
(defn build [dir]
  (let [artifact (:project (reader/read-string (core/slurp (str dir "/project.edn"))))
        _ (println "building" artifact)
        result (sh "boot" "build" :dir dir)]
    (when-not (= 0 (:exit result))
      (bail "...build failed"))
    (let [re #"Installed Version: ([^\s]+)"
          [_ version] (re-find re (:out result))]
      (println "...built version" version)
      (swap! new-deps assoc (symbol artifact) version))))


;; an inefficient "busy sleep" but it doesn't really matter
(defn sleep [msec]
  (let [deadline (+ msec (.getTime (js/Date.)))]
    (while (> deadline (.getTime (js/Date.))))))

(defn url [artifact version]
  (str
    "http://maven.arachne-framework.org/artifactory/arachne-dev/"
    (str/replace (str artifact) "." "/")
    "/"
    version
    "/"
    (name artifact)
    "-"
    version
    ".jar"))

(defn await [artifact version]
  (println "...fetching" artifact version)
  (loop [the-url (url artifact version)]
    (let [resp (http/get the-url)]
      (if (= 200 (:status resp))
        (println "......got it!")
        (do
          (println "......not yet ready...")
          (sleep 10000)
          (recur the-url))))))

(println "building all")
(doseq [dir dirs]
  (ensure-clean-git dir)
  (maybe-replace-deps dir)
  (build dir))

(doseq [dir dirs]
  (println "pushing" dir)
  (ensure-clean-git dir)
  (let [result (:out (sh "git" "status" :dir dir))]
    (if (re-find #"Your branch is up-to-date" result)
      (println "...not pushing," dir "is already up to date")
      (do
        (println "...pushing to origin/master")
        (when-not (= 0 (:exit (sh "git" "push" :dir dir)))
          (bail "...push failed!")))))
  (let [_ "...building locally..."
        build-result (sh "boot" "build" :dir dir)
        version-re #"Installed Version: ([^\s]+)"
        [_ version] (re-find version-re (:out build-result))
        artifact (symbol (:project (reader/read-string (core/slurp (str dir "/project.edn")))))]
    (await artifact version)))




