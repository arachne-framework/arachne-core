(ns arachne.core.util
  (:require [clojure.java.io :as io]
            [clojure.spec :as s]
            [clojure.string :as str])
  (:import [java.io FileNotFoundException])
  (:refer-clojure :exclude [alias]))

(def error-registry (atom {}))

(defn deferror
  "Add an error message to the error mesage registry"
  [key msg]
  (swap! error-registry assoc key msg))

(defn format-error-message
  "Given an error message string and an ex-data map, replace keywords in the
  string with their corresponding values (if present)"
  [msg ex-data]
  (str/replace msg #"(?::)([\S]*\w)"
    (fn [[match kw]]
      (str (get ex-data (keyword kw) match)))))

(defmacro error
  "Throw an ex-info with the given error message lookup key, optional cause, and
  ex-data map. The message string may contain :keywords which will be replaced
  by their corresponding values from the ex-data, if present.

  This is implemented as a macro so as to not show up in stack traces."
  [& [msg ex-data cause]]
  `(let [ex-data# ~ex-data
         template# (get @error-registry ~msg
                     (str "Unknown error message " ~msg))
         msg# (format-error-message template# ex-data#)]
     (throw (if ~cause
              (ex-info msg# ex-data# ~cause)
              (ex-info msg# ex-data#)))))

(defn read-edn
  "Read the given file from the classpath as EDN data"
  [file]
  (read-string {:readers *data-readers*} (slurp (io/resource file))))

(deferror ::could-not-load-ns
  "Could not load namespace :ns while attempting to resolve :s")

(deferror ::var-does-not-exist
  "Could not resolve :s; the specified var does exist.")

(defn require-and-resolve
  "Resolve a namespaced symbol (or string, or keyword representation of a
  symbol), first requiring its namespace. Throw a friendly error if the name
  could not be resolved."
  [s]
  (let [sym (cond
              (string? s) (symbol s)
              (keyword? s) (symbol (namespace s) (name s))
              (symbol? s) s)]
    (try
      (require (symbol (namespace sym)))
      (catch FileNotFoundException e
        (error ::could-not-load-ns {:ns (namespace sym)
                                    :s s
                                    :sym sym} e)))
    (let [var (resolve sym)]
      (when-not var
        (error ::var-does-not-exist {:s s, :sym sym}))
      var)))

(deferror ::args-do-not-conform
  "Arguments to :fn-sym did not conform to registered spec:\n :explain-str")

(defn validate-args
  "Given a fully qualified symbol naming a function and a some number of
  arguments, assert that the given arguments are valid according to the spec
  attached to the function. If not, throw an exception with an explanation."
  [fn-sym & args]
  (let [argspec (:args (s/get-spec fn-sym))]
    (when-not (s/valid? argspec args)
      (let [explain-str (s/explain-str argspec args)]
        (error ::args-do-not-conform {:fn-sym      fn-sym
                                      :argspec     argspec
                                      :explain-str explain-str})))))

(defmacro lazy-satisfies?
  "Returns a partial application of clojure.core/satisfies? that doesn't resolve
  its protocol until runtime. "
  [sym]
  `(fn [obj#]
     (when-let [protocol# @(resolve '~sym)]
       (satisfies? protocol# obj#))))

(defmacro lazy-instance?
  "Returns a partial application of clojure.core/instance? that doesn't resolve
  its class name until runtime. "
  [sym]
  `(fn [obj#]
     (when-let [class# (resolve '~sym)]
       (instance? class# obj#))))