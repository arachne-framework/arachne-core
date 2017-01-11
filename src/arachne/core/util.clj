(ns arachne.core.util
  (:require [clojure.java.io :as io]
            [clojure.spec :as s]
            [clojure.string :as str]
            [arachne.core.util.specs]
            [arachne.error :as e :refer [deferror error]])
  (:import [java.io FileNotFoundException])
  (:refer-clojure :exclude [alias]))

(defn read-edn
  "Read the given file from the classpath as EDN data"
  [file]
  (read-string {:readers *data-readers*} (slurp (io/resource file))))

(deferror ::could-not-load-ns
  :message "Could not load namespace `:ns` while attempting to resolve `:s`"
  :explanation "Some code attempted to \"require and resolve\" a symbol named `:s`. However, Clojure couldn't successfully `require` the namespace of that symbol, `:ns`."
  :suggestions ["Make sure the namespace `:ns` exists and is on the classpath"
                "Make sure the source file for `:ns` is named appropriately (remember to substitute '_' for '-')"
                "Make sure there are no typos in `:ns` or its namespace declaration."
                "Make sure that there are no compile errors in `:ns` (try loading it directly from the REPL)"
                "See the \"cause\" of this error for more information"]
  :ex-data-docs {:ns "The namespace of the attempted symbol"
                 :s "The argument to `require-and-resolve`"
                 :sym "The attempted symbol"})

(deferror ::var-does-not-exist
  :message "Could not resolve `:s`; the specified var does not exist."
  :explanation "Some code attempted to \"require and resolve\" a symbol named `:s`. Although it succesfully found the namespace, Clojure couldn't successfully locate a var named `:s` using `clojure.core/resolve`"
  :suggestions ["Make sure that `:s` is defined"
                "Make sure that `:s` is publicly visible (i.e, does not have ^:private metadata)"]
  :ex-data-docs {:s "The argument to `require-and-resolve`"
                 :sym "The attempted symbol"})


(defn require-and-resolve
  "Resolve a namespaced symbol (or string, or keyword representation of a
  symbol), first requiring its namespace. Throw a friendly error if the name
  could not be resolved."
  [s]
  (locking require-and-resolve
    (e/assert-args `require-and-resolve s)
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
        var))))

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

(defn mkeep
  "Returns the given map, with all entries with nil or empty values removed"
  [m]
  (into {} (filter (fn [[_ v]]
                     (not (or (nil? v)
                            (and (coll? v) (empty? v))))) m)))

(deferror ::arity-detection-error
  :message "Could not detect the arity of `:f`"
  :explanation "Some code attempted to determine the arity of a function (`:f`) using JVM reflection. However, `:f` does not seem to support Clojure's internal `invoke` or `doInvoke` methods, meaning it doesn't meet Clojure's definition of a function. Therefore, it's arity could not be determined."
  :suggestions ["Make sure that `:f` is actually a Clojure function"]
  :ex-data-docs {:f "The function in question"})

(defn arity
  "Given a function, return its arity (or :many if the function is variadic)"
  [f]
  (let [methods (.getDeclaredMethods (class f))
        invokes (filter #(= "invoke" (.getName %)) methods)
        do-invokes (filter #(= "doInvoke" (.getName %)) methods)]
    (cond
      (not (empty? do-invokes)) :many
      (< 1 (count invokes)) :many
      (empty? invokes) (error ::arity-detection-error {:f f})
      :else (count (.getParameterTypes (first invokes))))))

(defn map-transform
  "Utility function for transforming maps into similar maps, which is a common
   but annoying task.

   This utility makes it easier to deal with optional/missing keys and value transformations.

   Takes an input map, an output base map and a series of 'mapping' triples.

   A mapping triple has three elements:

   1. The key in the input map.
   2. The attribute key in the output map.
   3. A function to apply to the value during the mapping.

   If a key is missing from the input map, it will not be placed into the output map.

   For example:

   (map-transform {:a 1 :b 2} {}
     :a :foo/a identity
     :b :foo/b str
     :c :foo/c identity)

   yields:

   {:foo/a 1, :foo/b \"2\"}"
  [input output & mappings]
  (let [triples (partition 3 mappings)
        omit? #(or (nil? %) (and (coll? %) (empty? %)))]
    (reduce (fn [output [src-key dest-key xform]]
              (if (omit? (src-key input))
                output
                (let [v (xform (src-key input))]
                  (if (omit? v)
                    output
                    (assoc output dest-key v)))))
      output triples)))


(defmacro keys**
  "Define a regex alt spec that will either match a map, or a sequence of alternating keys/values
   (s/keys or s/keys*). Arguments are as to s/keys*."
  [& body]
  `(s/alt
     :kwargs (s/keys* ~@body)
     :opts-map (s/keys ~@body)))