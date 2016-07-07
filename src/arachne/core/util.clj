(ns arachne.core.util
  (:require [clojure.java.io :as io])
  (:import [java.io FileNotFoundException])
  (:refer-clojure :exclude [alias]))

(defn read-edn
  "Read the given file from the classpath as EDN data"
  [file]
  (read-string {:readers *data-readers*} (slurp (io/resource file))))

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
        (throw
          (ex-info
            (format "Could not load namespace %s while attempting to resolve %s"
              (namespace sym) s) {:s s, :sym sym} e))))
    (let [var (resolve sym)]
      (when-not var
        (throw
          (ex-info
            (format "Could not resolve %s; the specified var does exist." s)
            {:s s, :sym sym})))
      var)))

(defmacro fail
  "Throw an exception using a formated error message and an optional data map.

   Is a macro instead of a function so it will not make stack traces more
   complicated."
  [msg & args]
  `(let [args# [~@args]]
     (throw (ex-info (apply format ~msg args#) (if (map? (last args#))
                                                 (last args#)
                                                 {})))))