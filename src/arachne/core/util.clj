(ns arachne.core.util
  (:require [clojure.java.io :as io]
            [clojure.spec :as spec])
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

(defmacro assert-args
  "Given a symbol naming a function in the current ns, and some number of
  arguments, assert that the given arguments are valid according to the spec
  attached to the function. If not, throw an exception with an explanation.

  Is a macro instead of a function, so as not to make stack traces more
  complicated."
  [fn-sym & args]
  `(let [fn-var# (resolve '~fn-sym)
         argspec# (:args (spec/get-spec fn-var#))
         argseq# [~@args]]
     (when-not (spec/valid? argspec# argseq#)
       (let [explain-str# (spec/explain-str argspec# argseq#)]
         (throw
           (ex-info
             (format "Arguments to %s did not conform to registered spec:\n %s"
                     fn-var#
                     explain-str#)
             {:sym         ~fn-sym
              :var         fn-var#
              :argspec     argspec#
              :explain-str explain-str#}))))))

(defmacro satisfies-pred
  "Yield a predicate function that tests if a value satisfies the given
  protocol. The protocol is not resolved until runtime."
  [protocol-sym]
  `(fn [val#]
     (satisfies? (resolve '~protocol-sym) val#)))